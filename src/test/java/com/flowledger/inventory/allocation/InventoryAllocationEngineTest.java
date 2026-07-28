package com.flowledger.inventory.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.flowledger.inventory.allocation.strategy.FifoAllocationStrategy;
import com.flowledger.inventory.entity.InventoryBatch;
import com.flowledger.inventory.repository.InventoryBatchRepository;
import com.flowledger.inventory.repository.InventoryTransactionRepository;
import com.flowledger.inventory.repository.StockReservationRepository;
import com.flowledger.inventory.service.StockReservationService;
import com.flowledger.organization.entity.OrganizationSettings;
import com.flowledger.organization.repository.OrganizationSettingsRepository;
import com.flowledger.product.entity.Product;
import com.flowledger.product.repository.ProductRepository;
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
class InventoryAllocationEngineTest {
    @Mock
    ProductRepository products;

    @Mock
    OrganizationSettingsRepository orgSettings;

    @Mock
    WarehouseRepository warehouses;

    @Mock
    InventoryBatchRepository batches;

    @Mock
    InventoryTransactionRepository transactions;

    @Mock
    StockReservationRepository reservations;

    @Mock
    StockReservationService reservationService;

    private InventoryAllocationEngine engine;

    @BeforeEach
    void setUp() {
        ReservationAvailabilityService availability =
                new ReservationAvailabilityService(transactions, reservations, batches);
        AllocationCandidateProvider provider = new AllocationCandidateProvider(batches, warehouses, reservations);
        BatchAllocationEngine batchEngine = new BatchAllocationEngine(
                provider,
                batches,
                availability,
                new FifoAllocationStrategy(),
                new com.flowledger.inventory.allocation.strategy.FefoAllocationStrategy(),
                new com.flowledger.inventory.allocation.strategy.LifoAllocationStrategy(),
                new com.flowledger.inventory.allocation.strategy.HighestQuantityAllocationStrategy(),
                new com.flowledger.inventory.allocation.strategy.PreferredWarehouseAllocationStrategy());
        engine = new InventoryAllocationEngineImpl(
                products,
                orgSettings,
                warehouses,
                new WarehousePoolAllocator(availability),
                batchEngine,
                reservationService);
    }

    @Test
    void routesNonBatchProductToWarehousePool() {
        UUID org = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID warehouse = UUID.randomUUID();
        Product product = new Product();
        product.setBatchTracking(false);
        when(products.findByIdAndOrganizationId(productId, org)).thenReturn(Optional.of(product));
        when(transactions.stockBalance(org, productId, warehouse)).thenReturn(new BigDecimal("5"));
        when(reservations.activeReservedQty(org, productId, warehouse, null)).thenReturn(BigDecimal.ZERO);

        AllocationResult result =
                engine.allocate(new AllocationRequest(org, productId, warehouse, BigDecimal.ONE, null));

        assertEquals(AllocationStatus.AUTO_ALLOCATED, result.status());
        assertEquals(AllocationMode.WAREHOUSE_POOL, result.allocated().allocationMode());
    }

    @Test
    void checkoutRevalidationDetectsBatchChange() {
        UUID org = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID warehouse = UUID.randomUUID();
        UUID lineId = UUID.randomUUID();
        UUID batchA = UUID.randomUUID();
        UUID batchB = UUID.randomUUID();

        Product product = new Product();
        product.setBatchTracking(true);
        when(products.findByIdAndOrganizationId(productId, org)).thenReturn(Optional.of(product));

        OrganizationSettings settings = new OrganizationSettings();
        settings.setAllocationStrategy("FIFO");
        when(orgSettings.findByOrganizationId(org)).thenReturn(Optional.of(settings));
        when(warehouses.findFirstByOrganizationIdAndDefaultWarehouseTrue(org)).thenReturn(Optional.empty());
        when(warehouses.findByOrganizationId(org)).thenReturn(List.of());

        InventoryBatch older = batch(batchA, org, productId, warehouse, LocalDate.of(2026, 1, 1));
        InventoryBatch newer = batch(batchB, org, productId, warehouse, LocalDate.of(2026, 2, 1));
        when(batches.findByOrganizationIdAndProductIdAndWarehouseIdAndQualityStatusOrderByReceivedDateAscExpiryDateAsc(
                        org, productId, warehouse, "AVAILABLE"))
                .thenReturn(List.of(newer, older));
        when(reservations.activeReservedQtyByBatch(org, batchA, null)).thenReturn(BigDecimal.ZERO);
        when(reservations.activeReservedQtyByBatch(org, batchB, null)).thenReturn(BigDecimal.ZERO);

        CheckoutValidationResult validation = engine.revalidateForCheckout(List.of(new CartLineAllocation(
                org, lineId, productId, warehouse, BigDecimal.ONE, batchB, AllocationMode.BATCH_AUTO)));

        assertFalse(validation.isOk());
        assertEquals(AllocationStatus.CONFLICT, validation.status());
        assertTrue(validation.issues().get(0).allocationChanged());
    }

    private static InventoryBatch batch(UUID id, UUID org, UUID productId, UUID warehouse, LocalDate received) {
        InventoryBatch batch = new InventoryBatch();
        batch.setId(id);
        batch.setOrganizationId(org);
        batch.setProductId(productId);
        batch.setWarehouseId(warehouse);
        batch.setBatchNumber("B-" + id.toString().substring(0, 8));
        batch.setReceivedDate(received);
        batch.setQualityStatus("AVAILABLE");
        batch.setQuantity(BigDecimal.TEN);
        return batch;
    }
}
