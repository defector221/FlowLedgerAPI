package com.flowledger.inventory.allocation.strategy;

import com.flowledger.inventory.allocation.AllocationCandidate;
import com.flowledger.inventory.allocation.AllocationContext;
import com.flowledger.inventory.allocation.AllocationStrategy;
import com.flowledger.inventory.allocation.AllocationStrategyType;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

@Component
public class FifoAllocationStrategy implements AllocationStrategy {

    @Override
    public AllocationStrategyType type() {
        return AllocationStrategyType.FIFO;
    }

    @Override
    public List<AllocationCandidate> rank(List<AllocationCandidate> eligible, AllocationContext context) {
        return eligible.stream()
                .sorted(Comparator.comparing(
                                AllocationCandidate::receivedDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AllocationCandidate::batchId))
                .toList();
    }

    @Override
    public boolean ties(AllocationCandidate a, AllocationCandidate b) {
        return Objects.equals(a.receivedDate(), b.receivedDate());
    }
}
