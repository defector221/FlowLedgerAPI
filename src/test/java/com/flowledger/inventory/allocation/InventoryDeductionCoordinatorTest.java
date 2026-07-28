package com.flowledger.inventory.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowledger.inventory.entity.StockReservation;
import com.flowledger.inventory.entity.StockReservation.Status;
import com.flowledger.inventory.service.InventoryService;
import com.flowledger.inventory.service.StockReservationService;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationSettingsRepository;
import com.flowledger.product.entity.Product;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.sales.entity.DeliveryChallanItem;
import com.flowledger.sales.entity.SalesInvoice;
import com.flowledger.sales.entity.SalesInvoiceItem;
import com.flowledger.sales.entity.SalesOrderItem;
import com.flowledger.warehouse.repository.WarehouseRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryDeductionCoordinatorTest {
    @Mock
    InventoryAllocationEngine allocationEngine;

    @Mock
    StockReservationService reservations;

    @Mock
    InventoryService inventory;

    @Mock
    OrganizationSettingsRepository orgSettings;

    @Mock
    WarehouseRepository warehouses;

    @Mock
    ProductRepository products;

    InventoryDeductionCoordinator coordinator;

    UUID orgId = UUID.randomUUID();
    UUID productId = UUID.randomUUID();
    UUID warehouseId = UUID.randomUUID();
    UUID batchId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        coordinator = new InventoryDeductionCoordinator(
                allocationEngine, reservations, inventory, orgSettings, warehouses, products);
    }

    @Test
    void transferOrderLineToChallanSplitsReservationForPartialQty() {
        UUID orderLineId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();
        UUID challanId = UUID.randomUUID();
        UUID challanLineId = UUID.randomUUID();

        SalesOrderItem orderLine = new SalesOrderItem();
        orderLine.setId(orderLineId);
        orderLine.setProductId(productId);
        orderLine.setQuantity(new BigDecimal("10"));
        orderLine.setWarehouseId(warehouseId);
        orderLine.setStockReservationId(reservationId);
        orderLine.setInventoryBatchId(batchId);
        orderLine.setAllocationMode("BATCH_AUTO");

        DeliveryChallanItem challanItem = new DeliveryChallanItem();
        challanItem.setId(challanLineId);
        challanItem.setProductId(productId);

        Product product = new Product();
        product.setItemType("PRODUCT");
        when(products.findByIdAndOrganizationId(productId, orgId)).thenReturn(Optional.of(product));

        StockReservation child = new StockReservation();
        child.setId(UUID.randomUUID());
        child.setInventoryBatchId(batchId);
        child.setWarehouseId(warehouseId);
        child.setAllocationMode("BATCH_AUTO");
        when(reservations.splitReservation(
                        reservationId,
                        new BigDecimal("4"),
                        InventoryDeductionCoordinator.REF_DELIVERY_CHALLAN,
                        challanId,
                        challanLineId))
                .thenReturn(child);

        coordinator.transferOrderLineToChallan(
                orgId, orderLine, challanItem, challanId, challanLineId, new BigDecimal("4"));

        assertEquals(child.getId(), challanItem.getStockReservationId());
        assertEquals(batchId, challanItem.getInventoryBatchId());
        assertEquals(reservationId, orderLine.getStockReservationId());
    }

    @Test
    void deductOnInvoiceConfirmConsumesReservationAndPostsBatchSale() {
        UUID invoiceId = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();

        Organization org = new Organization();
        org.setId(orgId);
        org.setAllowNegativeStock(true);

        SalesInvoice invoice = new SalesInvoice();
        invoice.setId(invoiceId);
        invoice.setOrganizationId(orgId);
        invoice.setWarehouseId(warehouseId);
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setInvoiceNumber("INV-1");

        SalesInvoiceItem line = new SalesInvoiceItem();
        line.setId(lineId);
        line.setProductId(productId);
        line.setQuantity(new BigDecimal("2"));
        line.setInventoryBatchId(batchId);
        line.setWarehouseId(warehouseId);
        line.setStockReservationId(reservationId);

        StockReservation reservation = new StockReservation();
        reservation.setId(reservationId);
        reservation.setStatus(Status.ACTIVE);
        when(reservations.findById(reservationId)).thenReturn(Optional.of(reservation));

        coordinator.deductOnInvoiceConfirm(invoice, org, List.of(line));

        verify(reservations).consume(reservationId);
        verify(inventory)
                .postPosSale(
                        warehouseId,
                        productId,
                        new BigDecimal("2"),
                        batchId,
                        invoice.getInvoiceDate(),
                        invoiceId,
                        "INV-1",
                        "invoice:" + invoiceId + ":" + lineId);
    }

    @Test
    void deductOnInvoiceConfirmSkipsAlreadyConsumedReservation() {
        UUID reservationId = UUID.randomUUID();
        Organization org = new Organization();
        org.setId(orgId);

        SalesInvoice invoice = new SalesInvoice();
        invoice.setId(UUID.randomUUID());
        invoice.setOrganizationId(orgId);
        invoice.setWarehouseId(warehouseId);
        invoice.setInvoiceDate(LocalDate.now());

        SalesInvoiceItem line = new SalesInvoiceItem();
        line.setId(UUID.randomUUID());
        line.setProductId(productId);
        line.setQuantity(BigDecimal.ONE);
        line.setStockReservationId(reservationId);

        StockReservation reservation = new StockReservation();
        reservation.setStatus(Status.CONSUMED);
        when(reservations.findById(reservationId)).thenReturn(Optional.of(reservation));

        coordinator.deductOnInvoiceConfirm(invoice, org, List.of(line));

        verify(reservations, never()).consume(any());
        verify(inventory, never()).postPosSale(any(), any(), any(), any(), any(), any(), any(), any());
    }
}
