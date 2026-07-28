package com.flowledger.inventory.allocation.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flowledger.inventory.allocation.AllocationCandidate;
import com.flowledger.inventory.allocation.AllocationContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FifoAllocationStrategyTest {
    private final FifoAllocationStrategy strategy = new FifoAllocationStrategy();

    @Test
    void ordersByReceivedDateAscending() {
        UUID batchOld = UUID.randomUUID();
        UUID batchNew = UUID.randomUUID();
        List<AllocationCandidate> ranked = strategy.rank(
                List.of(candidate(batchNew, LocalDate.of(2026, 2, 1)), candidate(batchOld, LocalDate.of(2026, 1, 1))),
                new AllocationContext(UUID.randomUUID(), UUID.randomUUID(), null));
        assertEquals(batchOld, ranked.get(0).batchId());
    }

    @Test
    void tieWhenSameReceivedDate() {
        LocalDate received = LocalDate.of(2026, 1, 15);
        AllocationCandidate a = candidate(UUID.randomUUID(), received);
        AllocationCandidate b = candidate(UUID.randomUUID(), received);
        assertTrue(strategy.ties(a, b));
    }

    @Test
    void noTieWhenDifferentReceivedDate() {
        assertFalse(strategy.ties(
                candidate(UUID.randomUUID(), LocalDate.of(2026, 1, 1)),
                candidate(UUID.randomUUID(), LocalDate.of(2026, 2, 1))));
    }

    private static AllocationCandidate candidate(UUID batchId, LocalDate received) {
        return new AllocationCandidate(
                batchId,
                UUID.randomUUID(),
                "Main",
                "B1",
                BigDecimal.TEN,
                null,
                received,
                null,
                "AVAILABLE",
                BigDecimal.ZERO);
    }
}
