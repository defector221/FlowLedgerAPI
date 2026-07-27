package com.flowledger.inventory.allocation;

import com.flowledger.inventory.entity.InventoryBatch;
import com.flowledger.inventory.repository.InventoryBatchRepository;
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

    public AllocationCandidateProvider(InventoryBatchRepository batches, WarehouseRepository warehouses) {
        this.batches = batches;
        this.warehouses = warehouses;
    }

    public List<AllocationCandidate> eligibleBatches(UUID orgId, UUID productId, UUID warehouseId, BigDecimal qty) {
        LocalDate today = LocalDate.now();
        List<InventoryBatch> rows =
                batches.findByOrganizationIdAndProductIdAndWarehouseIdAndQualityStatusOrderByReceivedDateAscExpiryDateAsc(
                        orgId, productId, warehouseId, "AVAILABLE");
        Map<UUID, String> warehouseNames = warehouses.findByOrganizationId(orgId).stream()
                .collect(Collectors.toMap(Warehouse::getId, w -> w.getWarehouseName(), (a, b) -> a));

        List<AllocationCandidate> eligible = new ArrayList<>();
        for (InventoryBatch batch : rows) {
            if (batch.getExpiryDate() != null && batch.getExpiryDate().isBefore(today)) {
                continue;
            }
            BigDecimal available = n(batch.getQuantity());
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
                    BigDecimal.ZERO));
        }
        return eligible;
    }

    public AllocationCandidate toCandidate(InventoryBatch batch) {
        String warehouseName = warehouses
                .findByIdAndOrganizationId(batch.getWarehouseId(), batch.getOrganizationId())
                .map(Warehouse::getWarehouseName)
                .orElse(null);
        return new AllocationCandidate(
                batch.getId(),
                batch.getWarehouseId(),
                warehouseName,
                batch.getBatchNumber(),
                n(batch.getQuantity()),
                batch.getExpiryDate(),
                batch.getReceivedDate(),
                batch.getLotNumber(),
                batch.getQualityStatus(),
                BigDecimal.ZERO);
    }

    private static BigDecimal n(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
