package com.flowledger.inventory.allocation;

import java.util.List;

/** Outcome of checkout inventory re-validation. */
public record CheckoutValidationResult(
        AllocationStatus status,
        List<CheckoutLineIssue> issues,
        List<CartLineAllocation> resolvedLines) {

    public static CheckoutValidationResult ok(List<CartLineAllocation> resolved) {
        return new CheckoutValidationResult(AllocationStatus.AUTO_ALLOCATED, List.of(), resolved);
    }

    public boolean isOk() {
        return status == AllocationStatus.AUTO_ALLOCATED && issues.isEmpty();
    }
}
