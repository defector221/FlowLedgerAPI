package com.flowledger.sales.dto;

import com.flowledger.inventory.allocation.AllocationCandidate;
import com.flowledger.inventory.allocation.AllocationStatus;
import com.flowledger.inventory.allocation.ReservationResult;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class SalesAllocationDtos {
    private SalesAllocationDtos() {}

    public record ConfirmOrderRequest(UUID warehouseId) {}

    public record ConfirmLineAllocationRequest(UUID batchId, String allocationMode) {}

    public record ConvertToChallanLineRequest(
            @NotNull UUID orderLineId, @NotNull @Positive BigDecimal quantity) {}

    public record ConvertToChallanRequest(UUID warehouseId, List<ConvertToChallanLineRequest> lines) {}

    public record AllocationCandidateResponse(
            UUID batchId,
            UUID warehouseId,
            String warehouseName,
            String batchNumber,
            BigDecimal availableQty,
            LocalDate expiryDate,
            LocalDate receivedDate,
            String lotNumber,
            String qualityStatus,
            BigDecimal reservedQty) {

        public static AllocationCandidateResponse from(AllocationCandidate c) {
            return new AllocationCandidateResponse(
                    c.batchId(),
                    c.warehouseId(),
                    c.warehouseName(),
                    c.batchNumber(),
                    c.availableQty(),
                    c.expiryDate(),
                    c.receivedDate(),
                    c.lotNumber(),
                    c.qualityStatus(),
                    c.reservedQty());
        }
    }

    public record SalesAllocationConflictResponse(
            String status,
            UUID lineId,
            UUID productId,
            String message,
            List<AllocationCandidateResponse> candidates) {

        public static SalesAllocationConflictResponse from(UUID lineId, UUID productId, ReservationResult result) {
            return new SalesAllocationConflictResponse(
                    result.status().name(),
                    lineId,
                    productId,
                    result.reason() != null ? result.reason() : result.status().name(),
                    result.candidates().stream().map(AllocationCandidateResponse::from).toList());
        }
    }
}
