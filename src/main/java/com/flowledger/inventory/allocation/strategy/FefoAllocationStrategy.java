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
public class FefoAllocationStrategy implements AllocationStrategy {

    @Override
    public AllocationStrategyType type() {
        return AllocationStrategyType.FEFO;
    }

    @Override
    public List<AllocationCandidate> rank(List<AllocationCandidate> eligible, AllocationContext context) {
        return eligible.stream()
                .sorted(Comparator.comparing(
                                AllocationCandidate::expiryDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(
                                AllocationCandidate::receivedDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(AllocationCandidate::batchId))
                .toList();
    }

    @Override
    public boolean ties(AllocationCandidate a, AllocationCandidate b) {
        return Objects.equals(a.expiryDate(), b.expiryDate()) && Objects.equals(a.receivedDate(), b.receivedDate());
    }
}
