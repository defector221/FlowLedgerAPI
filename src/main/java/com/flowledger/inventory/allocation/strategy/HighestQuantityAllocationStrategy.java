package com.flowledger.inventory.allocation.strategy;

import com.flowledger.inventory.allocation.AllocationCandidate;
import com.flowledger.inventory.allocation.AllocationContext;
import com.flowledger.inventory.allocation.AllocationStrategy;
import com.flowledger.inventory.allocation.AllocationStrategyType;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class HighestQuantityAllocationStrategy implements AllocationStrategy {

    @Override
    public AllocationStrategyType type() {
        return AllocationStrategyType.HIGHEST_QUANTITY;
    }

    @Override
    public List<AllocationCandidate> rank(List<AllocationCandidate> eligible, AllocationContext context) {
        return eligible.stream()
                .sorted(Comparator.comparing(AllocationCandidate::availableQty, Comparator.reverseOrder())
                        .thenComparing(AllocationCandidate::batchId))
                .toList();
    }

    @Override
    public boolean ties(AllocationCandidate a, AllocationCandidate b) {
        return a.availableQty().compareTo(b.availableQty()) == 0;
    }
}
