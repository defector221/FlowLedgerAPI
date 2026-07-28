package com.flowledger.inventory.allocation;

import java.util.List;

/** Result returned by {@link InventoryAllocationEngine}. */
public record AllocationResult(
        AllocationStatus status, AllocatedInventory allocated, List<AllocationCandidate> candidates, String reason) {

    public static AllocationResult auto(AllocatedInventory allocated) {
        return new AllocationResult(AllocationStatus.AUTO_ALLOCATED, allocated, List.of(), null);
    }

    public static AllocationResult conflict(List<AllocationCandidate> candidates) {
        return new AllocationResult(AllocationStatus.CONFLICT, null, candidates, "Multiple inventory candidates");
    }

    public static AllocationResult outOfStock(String reason) {
        return new AllocationResult(AllocationStatus.OUT_OF_STOCK, null, List.of(), reason);
    }

    public static AllocationResult invalidProduct(String reason) {
        return new AllocationResult(AllocationStatus.INVALID_PRODUCT, null, List.of(), reason);
    }
}
