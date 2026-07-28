package com.flowledger.inventory.allocation;

import com.flowledger.inventory.entity.StockReservation;
import com.flowledger.inventory.entity.StockReservation.Status;
import com.flowledger.inventory.service.InventoryService;
import com.flowledger.inventory.service.StockReservationService;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.entity.OrganizationSettings;
import com.flowledger.organization.repository.OrganizationSettingsRepository;
import com.flowledger.product.entity.Product;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.sales.entity.DeliveryChallanItem;
import com.flowledger.sales.entity.SalesInvoice;
import com.flowledger.sales.entity.SalesInvoiceItem;
import com.flowledger.sales.entity.SalesOrderItem;
import com.flowledger.sales.exception.SalesAllocationConflictException;
import com.flowledger.warehouse.repository.WarehouseRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Interprets org inventory_deduction_event and drives allocation/reservation lifecycle. */
@Service
@Transactional
public class InventoryDeductionCoordinator {
    public static final String REF_SALES_ORDER = "SALES_ORDER";
    public static final String REF_DELIVERY_CHALLAN = "DELIVERY_CHALLAN";
    public static final String REF_SALES_INVOICE = "SALES_INVOICE";
    public static final String REF_SHIPMENT = "SHIPMENT";

    private final InventoryAllocationEngine allocationEngine;
    private final StockReservationService reservations;
    private final InventoryService inventory;
    private final OrganizationSettingsRepository orgSettings;
    private final WarehouseRepository warehouses;
    private final ProductRepository products;

    public InventoryDeductionCoordinator(
            InventoryAllocationEngine allocationEngine,
            StockReservationService reservations,
            InventoryService inventory,
            OrganizationSettingsRepository orgSettings,
            WarehouseRepository warehouses,
            ProductRepository products) {
        this.allocationEngine = allocationEngine;
        this.reservations = reservations;
        this.inventory = inventory;
        this.orgSettings = orgSettings;
        this.warehouses = warehouses;
        this.products = products;
    }

    public ReservationResult reserveSalesOrderLine(
            UUID orgId,
            UUID productId,
            UUID warehouseId,
            BigDecimal quantity,
            UUID preferredBatchId,
            AllocationMode preferredMode,
            UUID orderId,
            UUID lineId) {
        if (!isStockedProduct(productId, orgId)) {
            return ReservationResult.reserved(null, null);
        }
        ReservationResult result = allocationEngine.reserveForDocument(new DocumentLineAllocationRequest(
                orgId,
                productId,
                warehouseId,
                quantity,
                preferredBatchId,
                preferredMode,
                REF_SALES_ORDER,
                orderId,
                lineId,
                null,
                null));
        if (result.status() == AllocationStatus.CONFLICT || result.status() == AllocationStatus.OUT_OF_STOCK) {
            throw new SalesAllocationConflictException(lineId, productId, result);
        }
        return result;
    }

    public void releaseSalesOrder(UUID orgId, UUID orderId) {
        allocationEngine.releaseByReference(REF_SALES_ORDER, orderId);
    }

    public void transferOrderLineToChallan(
            UUID orgId,
            SalesOrderItem orderLine,
            DeliveryChallanItem challanItem,
            UUID challanId,
            UUID challanLineId,
            BigDecimal qty) {
        if (!isStockedProduct(orderLine.getProductId(), orgId)) {
            return;
        }
        UUID warehouseId = orderLine.getWarehouseId();
        if (orderLine.getStockReservationId() == null) {
            challanItem.setInventoryBatchId(orderLine.getInventoryBatchId());
            challanItem.setWarehouseId(warehouseId);
            challanItem.setAllocationMode(orderLine.getAllocationMode());
            return;
        }

        StockReservation transferred = reservations.splitReservation(
                orderLine.getStockReservationId(), qty, REF_DELIVERY_CHALLAN, challanId, challanLineId);
        challanItem.setStockReservationId(transferred.getId());
        challanItem.setInventoryBatchId(transferred.getInventoryBatchId());
        challanItem.setWarehouseId(transferred.getWarehouseId());
        challanItem.setAllocationMode(transferred.getAllocationMode());

        if (qty.compareTo(orderLine.getQuantity()) >= 0) {
            orderLine.setStockReservationId(null);
            orderLine.setInventoryBatchId(null);
            orderLine.setAllocationMode(null);
        }
    }

    public void deductOnInvoiceConfirm(SalesInvoice invoice, Organization org, List<SalesInvoiceItem> stockableLines) {
        if (invoice.isInventoryPosted() || stockableLines.isEmpty()) {
            return;
        }
        UUID orgId = invoice.getOrganizationId();
        UUID defaultWarehouse = invoice.getWarehouseId();
        String event = deductionEvent(orgId);

        for (SalesInvoiceItem line : stockableLines) {
            UUID warehouseId = line.getWarehouseId() != null ? line.getWarehouseId() : defaultWarehouse;
            if (warehouseId == null) {
                throw new IllegalArgumentException("Warehouse is required when confirming stocked products");
            }

            UUID reservationId = line.getStockReservationId();
            if (reservationId != null) {
                StockReservation reservation =
                        reservations.findById(reservationId).orElse(null);
                if (reservation != null && reservation.getStatus() == Status.CONSUMED) {
                    continue;
                }
                if (reservation != null && reservation.getStatus() == Status.ACTIVE) {
                    reservations.consume(reservationId);
                }
            } else if (reservesAtInvoiceConfirm(event)) {
                ReservationResult result = allocationEngine.reserveForDocument(new DocumentLineAllocationRequest(
                        orgId,
                        line.getProductId(),
                        warehouseId,
                        line.getQuantity(),
                        line.getInventoryBatchId(),
                        parseMode(line.getAllocationMode()),
                        REF_SALES_INVOICE,
                        invoice.getId(),
                        line.getId(),
                        null,
                        null));
                if (result.status() == AllocationStatus.CONFLICT || result.status() == AllocationStatus.OUT_OF_STOCK) {
                    throw new SalesAllocationConflictException(line.getId(), line.getProductId(), result);
                }
                if (result.reservationId() != null) {
                    line.setStockReservationId(result.reservationId());
                }
                if (result.allocated() != null) {
                    line.setInventoryBatchId(result.allocated().batchId());
                    line.setAllocationMode(result.allocated().allocationMode().name());
                    line.setWarehouseId(result.allocated().warehouseId());
                }
                if (result.reservationId() != null) {
                    reservations.consume(result.reservationId());
                }
            }

            inventory.postPosSale(
                    warehouseId,
                    line.getProductId(),
                    line.getQuantity(),
                    line.getInventoryBatchId(),
                    invoice.getInvoiceDate(),
                    invoice.getId(),
                    invoice.getInvoiceNumber(),
                    "invoice:" + invoice.getId() + ":" + line.getId());
        }
    }

    public void deductOnShipmentDispatch(
            UUID orgId,
            UUID shipmentId,
            String shipmentNumber,
            UUID warehouseId,
            UUID productId,
            BigDecimal quantity,
            UUID sourceLineId,
            DeliveryChallanItem challanItem) {
        if (!isStockedProduct(productId, orgId)) {
            return;
        }
        UUID batchId = challanItem != null ? challanItem.getInventoryBatchId() : null;
        UUID reservationId = challanItem != null ? challanItem.getStockReservationId() : null;

        if (reservationId != null) {
            StockReservation reservation = reservations.findById(reservationId).orElse(null);
            if (reservation != null && reservation.getStatus() == Status.ACTIVE) {
                reservations.consume(reservationId);
                batchId = batchId != null ? batchId : reservation.getInventoryBatchId();
            } else if (reservation != null && reservation.getStatus() == Status.CONSUMED) {
                return;
            }
        }

        inventory.postPosSale(
                warehouseId,
                productId,
                quantity,
                batchId,
                LocalDate.now(),
                shipmentId,
                shipmentNumber,
                "shipment:" + shipmentId + ":" + sourceLineId);
    }

    public UUID resolveWarehouseId(UUID orgId, UUID requestedWarehouseId) {
        if (requestedWarehouseId != null) {
            return requestedWarehouseId;
        }
        return orgSettings
                .findByOrganizationId(orgId)
                .map(OrganizationSettings::getDefaultWarehouseId)
                .or(() -> warehouses
                        .findFirstByOrganizationIdAndDefaultWarehouseTrue(orgId)
                        .map(w -> w.getId()))
                .orElseThrow(() -> new IllegalArgumentException("Warehouse is required for sales order confirm"));
    }

    public boolean shouldReserveOnSalesOrderConfirm(UUID orgId) {
        String event = deductionEvent(orgId);
        return "SALES_ORDER".equalsIgnoreCase(event) || "CHALLAN_DISPATCH".equalsIgnoreCase(event);
    }

    public boolean deductsOnShipmentDispatch(UUID orgId) {
        return "CHALLAN_DISPATCH".equalsIgnoreCase(deductionEvent(orgId));
    }

    public String deductionEvent(UUID orgId) {
        return orgSettings
                .findByOrganizationId(orgId)
                .map(OrganizationSettings::getInventoryDeductionEvent)
                .orElse("INVOICE_CONFIRM");
    }

    private boolean reservesAtInvoiceConfirm(String event) {
        return event == null
                || event.isBlank()
                || "INVOICE_CONFIRM".equalsIgnoreCase(event)
                || "DELIVERY_CHALLAN".equalsIgnoreCase(event);
    }

    private AllocationMode parseMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return null;
        }
        try {
            return AllocationMode.valueOf(mode);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean isStockedProduct(UUID productId, UUID orgId) {
        Product product = products.findByIdAndOrganizationId(productId, orgId).orElse(null);
        if (product == null) {
            return false;
        }
        String type = product.getItemType();
        return type == null || type.isBlank() || "PRODUCT".equalsIgnoreCase(type);
    }
}
