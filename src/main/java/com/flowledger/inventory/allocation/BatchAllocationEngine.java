package com.flowledger.inventory.allocation;

import com.flowledger.inventory.allocation.strategy.*;
import com.flowledger.inventory.entity.InventoryBatch;
import com.flowledger.inventory.repository.InventoryBatchRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class BatchAllocationEngine {
    private final AllocationCandidateProvider candidateProvider;
    private final InventoryBatchRepository batches;
    private final Map<AllocationStrategyType, AllocationStrategy> strategies;

    public BatchAllocationEngine(
            AllocationCandidateProvider candidateProvider,
            InventoryBatchRepository batches,
            FifoAllocationStrategy fifo,
            FefoAllocationStrategy fefo,
            LifoAllocationStrategy lifo,
            HighestQuantityAllocationStrategy highestQuantity,
            PreferredWarehouseAllocationStrategy preferredWarehouse) {
        this.candidateProvider = candidateProvider;
        this.batches = batches;
        this.strategies = List.of(fifo, fefo, lifo, highestQuantity, preferredWarehouse).stream()
                .collect(Collectors.toMap(AllocationStrategy::type, Function.identity()));
    }

    public AllocationResult allocate(AllocationRequest request, AllocationStrategyType strategyType, UUID preferredWarehouseId) {
        List<AllocationCandidate> eligible = candidateProvider.eligibleBatches(
                request.organizationId(), request.productId(), request.warehouseId(), request.quantity());
        if (eligible.isEmpty()) {
            return AllocationResult.outOfStock("No eligible batches with sufficient quantity");
        }
        AllocationStrategy strategy = strategies.get(strategyType.effective());
        if (strategy == null) {
            strategy = strategies.get(AllocationStrategyType.FIFO);
        }
        AllocationContext context =
                new AllocationContext(request.organizationId(), request.warehouseId(), preferredWarehouseId);
        List<AllocationCandidate> ranked = strategy.rank(eligible, context);
        AllocationCandidate top = ranked.get(0);
        List<AllocationCandidate> ties = new ArrayList<>();
        for (AllocationCandidate candidate : ranked) {
            if (strategy.ties(top, candidate)) {
                ties.add(candidate);
            } else {
                break;
            }
        }
        if (ties.size() > 1) {
            return AllocationResult.conflict(ties);
        }
        return AllocationResult.auto(toAllocated(top, AllocationMode.BATCH_AUTO, request.quantity()));
    }

    public AllocationResult allocateWithBatch(AllocationRequest request, UUID batchId) {
        InventoryBatch batch = batches
                .findByIdAndOrganizationId(batchId, request.organizationId())
                .orElse(null);
        if (batch == null || !batch.getProductId().equals(request.productId())) {
            return AllocationResult.outOfStock("Selected batch is not available");
        }
        if (!"AVAILABLE".equalsIgnoreCase(batch.getQualityStatus())) {
            return AllocationResult.outOfStock("Selected batch is blocked or damaged");
        }
        if (batch.getExpiryDate() != null && batch.getExpiryDate().isBefore(java.time.LocalDate.now())) {
            return AllocationResult.outOfStock("Selected batch is expired");
        }
        if (n(batch.getQuantity()).compareTo(request.quantity()) < 0) {
            return AllocationResult.outOfStock("Insufficient quantity in selected batch");
        }
        return AllocationResult.auto(toAllocated(candidateProvider.toCandidate(batch), AllocationMode.BATCH_MANUAL, request.quantity()));
    }

    private AllocatedInventory toAllocated(AllocationCandidate candidate, AllocationMode mode, java.math.BigDecimal qty) {
        return new AllocatedInventory(
                candidate.batchId(),
                candidate.warehouseId(),
                candidate.warehouseName(),
                candidate.batchNumber(),
                qty,
                candidate.expiryDate(),
                candidate.receivedDate(),
                candidate.lotNumber(),
                mode);
    }

    private static java.math.BigDecimal n(java.math.BigDecimal v) {
        return v == null ? java.math.BigDecimal.ZERO : v;
    }
}
