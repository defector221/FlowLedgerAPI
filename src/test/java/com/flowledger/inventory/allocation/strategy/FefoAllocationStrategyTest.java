package com.flowledger.inventory.allocation.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flowledger.inventory.allocation.AllocationCandidate;
import com.flowledger.inventory.allocation.AllocationContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FefoAllocationStrategyTest {
    private final FefoAllocationStrategy strategy = new FefoAllocationStrategy();

    @Test
    void ordersByExpiryAscending() {
        UUID sooner = UUID.randomUUID();
        UUID later = UUID.randomUUID();
        List<AllocationCandidate> ranked = strategy.rank(
                List.of(candidate(later, LocalDate.of(2026, 12, 1)), candidate(sooner, LocalDate.of(2026, 6, 1))),
                new AllocationContext(UUID.randomUUID(), UUID.randomUUID(), null));
        assertEquals(sooner, ranked.get(0).batchId());
    }

    @Test
    void tieOnSameExpiryAndReceivedDate() {
        LocalDate expiry = LocalDate.of(2026, 6, 1);
        LocalDate received = LocalDate.of(2026, 1, 1);
        AllocationCandidate a = candidate(UUID.randomUUID(), expiry, received);
        AllocationCandidate b = candidate(UUID.randomUUID(), expiry, received);
        assertTrue(strategy.ties(a, b));
    }

    private static AllocationCandidate candidate(UUID batchId, LocalDate expiry) {
        return candidate(batchId, expiry, LocalDate.of(2026, 1, 1));
    }

    private static AllocationCandidate candidate(UUID batchId, LocalDate expiry, LocalDate received) {
        return new AllocationCandidate(
                batchId,
                UUID.randomUUID(),
                "Main",
                "B1",
                BigDecimal.TEN,
                expiry,
                received,
                null,
                "AVAILABLE",
                BigDecimal.ZERO);
    }
}
