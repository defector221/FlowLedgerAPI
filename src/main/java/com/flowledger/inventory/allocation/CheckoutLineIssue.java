package com.flowledger.inventory.allocation;

import java.util.List;
import java.util.UUID;

/** Per-line issue when checkout re-validation fails. */
public record CheckoutLineIssue(
        UUID lineId,
        UUID productId,
        AllocationStatus status,
        boolean allocationChanged,
        String message,
        List<AllocationCandidate> candidates) {}
