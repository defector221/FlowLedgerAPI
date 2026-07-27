package com.flowledger.inventory.allocation;

import java.util.List;
import java.util.UUID;

/** Outcome of reserveForDocument. */
public record ReservationResult(
        AllocationStatus status,
        UUID reservationId,
        AllocatedInventory allocated,
        List<AllocationCandidate> candidates,
        String reason) {

    public static ReservationResult reserved(UUID reservationId, AllocatedInventory allocated) {
        return new ReservationResult(AllocationStatus.AUTO_ALLOCATED, reservationId, allocated, List.of(), null);
    }

    public static ReservationResult conflict(List<AllocationCandidate> candidates) {
        return new ReservationResult(AllocationStatus.CONFLICT, null, null, candidates, "Multiple inventory candidates");
    }

    public static ReservationResult outOfStock(String reason) {
        return new ReservationResult(AllocationStatus.OUT_OF_STOCK, null, null, List.of(), reason);
    }

    public static ReservationResult invalidProduct(String reason) {
        return new ReservationResult(AllocationStatus.INVALID_PRODUCT, null, null, List.of(), reason);
    }
}
