package com.flowledger.inventory.allocation.strategy;

import com.flowledger.inventory.allocation.AllocationCandidate;
import com.flowledger.inventory.allocation.AllocationContext;
import com.flowledger.inventory.allocation.AllocationStrategy;
import com.flowledger.inventory.allocation.AllocationStrategyType;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PreferredWarehouseAllocationStrategy implements AllocationStrategy {

    @Override
    public AllocationStrategyType type() {
        return AllocationStrategyType.PREFERRED_WAREHOUSE;
    }

    @Override
    public List<AllocationCandidate> rank(List<AllocationCandidate> eligible, AllocationContext context) {
        UUID preferred =
                context.preferredWarehouseId() != null ? context.preferredWarehouseId() : context.warehouseId();
        return eligible.stream()
                .sorted(Comparator.comparing(
                                (AllocationCandidate c) -> !c.warehouseId().equals(preferred))
                        .thenComparing(
                                AllocationCandidate::receivedDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AllocationCandidate::batchId))
                .toList();
    }

    @Override
    public boolean ties(AllocationCandidate a, AllocationCandidate b) {
        return Objects.equals(a.warehouseId(), b.warehouseId()) && Objects.equals(a.receivedDate(), b.receivedDate());
    }
}
