package com.flowledger.inventory.allocation;

import java.util.List;

/** Strategy for ranking eligible batch candidates. */
public interface AllocationStrategy {
    AllocationStrategyType type();

    /**
     * Returns candidates ordered best-first. Ties at the top produce CONFLICT when more than one
     * shares the best rank key.
     */
    List<AllocationCandidate> rank(List<AllocationCandidate> eligible, AllocationContext context);

    /** Whether two candidates share the same rank (tie). */
    boolean ties(AllocationCandidate a, AllocationCandidate b);
}
