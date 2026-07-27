package com.flowledger.inventory.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowledger.inventory.entity.StockReservation;
import com.flowledger.inventory.service.StockReservationService;
import com.flowledger.organization.repository.OrganizationSettingsRepository;
import com.flowledger.product.entity.Product;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.warehouse.repository.WarehouseRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryAllocationEngineReservationTest {
    @Mock
    ProductRepository products;

    @Mock
    OrganizationSettingsRepository orgSettings;

    @Mock
    WarehouseRepository warehouses;

    @Mock
    WarehousePoolAllocator warehousePoolAllocator;

    @Mock
    BatchAllocationEngine batchAllocationEngine;

    @Mock
    StockReservationService reservations;

    @InjectMocks
    InventoryAllocationEngineImpl engine;

    @Test
    void reserveForDocumentCreatesReservationOnAutoAllocate() {
        UUID org = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID warehouse = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID reservationId = UUID.randomUUID();

        Product product = new Product();
        product.setBatchTracking(false);
        when(products.findByIdAndOrganizationId(productId, org)).thenReturn(Optional.of(product));

        AllocatedInventory allocated = new AllocatedInventory(
                null, warehouse, null, null, BigDecimal.ONE, null, null, null, AllocationMode.WAREHOUSE_POOL);
        when(warehousePoolAllocator.allocate(any())).thenReturn(AllocationResult.auto(allocated));

        StockReservation saved = new StockReservation();
        saved.setId(reservationId);
        when(reservations.reserve(
                        eq(productId),
                        eq(warehouse),
                        eq(BigDecimal.ONE),
                        eq(null),
                        eq(AllocationMode.WAREHOUSE_POOL),
                        eq("SALES_ORDER"),
                        eq(orderId),
                        eq(lineId),
                        eq(null)))
                .thenReturn(saved);

        ReservationResult result = engine.reserveForDocument(new DocumentLineAllocationRequest(
                org, productId, warehouse, BigDecimal.ONE, null, null, "SALES_ORDER", orderId, lineId, null, null));

        assertEquals(AllocationStatus.AUTO_ALLOCATED, result.status());
        assertEquals(reservationId, result.reservationId());
    }

    @Test
    void reserveForDocumentReturnsConflictWithoutReserving() {
        UUID org = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        Product product = new Product();
        product.setBatchTracking(false);
        when(products.findByIdAndOrganizationId(productId, org)).thenReturn(Optional.of(product));
        when(warehousePoolAllocator.allocate(any()))
                .thenReturn(AllocationResult.conflict(java.util.List.of()));

        ReservationResult result = engine.reserveForDocument(new DocumentLineAllocationRequest(
                org,
                productId,
                UUID.randomUUID(),
                BigDecimal.ONE,
                null,
                null,
                "SALES_ORDER",
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                null));

        assertEquals(AllocationStatus.CONFLICT, result.status());
    }
}
