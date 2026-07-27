package com.flowledger.sales.exception;

import com.flowledger.inventory.allocation.AllocationStatus;
import com.flowledger.inventory.allocation.ReservationResult;
import java.util.UUID;

public class SalesAllocationConflictException extends RuntimeException {
    private final UUID lineId;
    private final UUID productId;
    private final ReservationResult result;

    public SalesAllocationConflictException(ReservationResult result) {
        this(null, null, result);
    }

    public SalesAllocationConflictException(UUID lineId, UUID productId, ReservationResult result) {
        super(result.reason() != null ? result.reason() : result.status().name());
        this.lineId = lineId;
        this.productId = productId;
        this.result = result;
    }

    public UUID lineId() {
        return lineId;
    }

    public UUID productId() {
        return productId;
    }

    public ReservationResult result() {
        return result;
    }

    public AllocationStatus status() {
        return result.status();
    }
}
