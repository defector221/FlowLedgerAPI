package com.flowledger.inventory.allocation;

import com.flowledger.inventory.entity.InventoryBatch;
import com.flowledger.inventory.repository.InventoryBatchRepository;
import com.flowledger.inventory.repository.StockReservationRepository;
import com.flowledger.warehouse.entity.Warehouse;
import com.flowledger.warehouse.repository.WarehouseRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class AllocationCandidateProvider {
    private final InventoryBatchRepository batches;
    private final WarehouseRepository warehouses;
    private final StockReservationRepository reservations;

    public AllocationCandidateProvider(
            InventoryBatchRepository batches, WarehouseRepository warehouses, StockReservationRepository reservations) {
        this.batches = batches;
        this.warehouses = warehouses;
        this.reservations = reservations;
    }

    public List<AllocationCandidate> eligibleBatches(
            UUID orgId, UUID productId, UUID warehouseId, BigDecimal qty, UUID excludeReservationId) {
        LocalDate today = LocalDate.now();
        List<InventoryBatch> rows =
                batches
                        .findByOrganizationIdAndProductIdAndWarehouseIdAndQualityStatusOrderByReceivedDateAscExpiryDateAsc(
                                orgId, productId, warehouseId, "AVAILABLE");
        Map<UUID, String> warehouseNames = warehouses.findByOrganizationId(orgId).stream()
                .collect(Collectors.toMap(Warehouse::getId, w -> w.getWarehouseName(), (a, b) -> a));

        List<AllocationCandidate> eligible = new ArrayList<>();
        for (InventoryBatch batch : rows) {
            if (batch.getExpiryDate() != null && batch.getExpiryDate().isBefore(today)) {
                continue;
            }
            BigDecimal reserved = reservations.activeReservedQtyByBatch(orgId, batch.getId(), excludeReservationId);
            BigDecimal available = n(batch.getQuantity()).subtract(reserved).max(BigDecimal.ZERO);
            if (available.compareTo(qty) < 0) {
                continue;
            }
            eligible.add(new AllocationCandidate(
                    batch.getId(),
                    batch.getWarehouseId(),
                    warehouseNames.get(batch.getWarehouseId()),
                    batch.getBatchNumber(),
                    available,
                    batch.getExpiryDate(),
                    batch.getReceivedDate(),
                    batch.getLotNumber(),
                    batch.getQualityStatus(),
                    reserved));
        }
        return eligible;
    }

    public AllocationCandidate toCandidate(InventoryBatch batch, UUID orgId, UUID excludeReservationId) {
        String warehouseName = warehouses
                .findByIdAndOrganizationId(batch.getWarehouseId(), batch.getOrganizationId())
                .map(Warehouse::getWarehouseName)
                .orElse(null);
        BigDecimal reserved = reservations.activeReservedQtyByBatch(orgId, batch.getId(), excludeReservationId);
        BigDecimal available = n(batch.getQuantity()).subtract(reserved).max(BigDecimal.ZERO);
        return new AllocationCandidate(
                batch.getId(),
                batch.getWarehouseId(),
                warehouseName,
                batch.getBatchNumber(),
                available,
                batch.getExpiryDate(),
                batch.getReceivedDate(),
                batch.getLotNumber(),
                batch.getQualityStatus(),
                reserved);
    }

    public AllocationCandidate toCandidate(InventoryBatch batch) {
        return toCandidate(batch, batch.getOrganizationId(), null);
    }

    private static BigDecimal n(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
