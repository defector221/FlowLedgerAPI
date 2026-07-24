package com.flowledger.payment.dto;

import com.flowledger.payment.entity.Payment;
import com.flowledger.payment.entity.PaymentAllocation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class PaymentDtos {
    private PaymentDtos() {}

    public record Allocation(
            @NotBlank String documentType, @NotNull UUID documentId, @NotNull @DecimalMin("0.01") BigDecimal amount) {}

    public record PaymentRequest(
            @NotNull LocalDate paymentDate,
            @NotNull Payment.Type paymentType,
            @NotNull Payment.Party partyType,
            UUID customerId,
            UUID supplierId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotBlank String paymentMode,
            String transactionReference,
            String bankReference,
            String notes,
            List<@Valid Allocation> allocations) {}

    /** Bank/cash transfer between two GL accounts (CONTRA). */
    public record ContraRequest(
            @NotNull UUID fromAccountId,
            @NotNull UUID toAccountId,
            @NotNull @DecimalMin("0.01") BigDecimal amount,
            @NotNull LocalDate date,
            String notes,
            String transactionReference) {}

    public record AllocationResponse(
            UUID id, String documentType, UUID documentId, BigDecimal allocatedAmount, OffsetDateTime createdAt) {}

    public record PaymentResponse(
            UUID id,
            String paymentNumber,
            LocalDate paymentDate,
            Payment.Type paymentType,
            Payment.Party partyType,
            UUID customerId,
            UUID supplierId,
            UUID fromAccountId,
            UUID toAccountId,
            BigDecimal amount,
            String paymentMode,
            String transactionReference,
            String bankReference,
            String notes,
            String status,
            BigDecimal allocatedAmount,
            BigDecimal unallocatedAmount,
            List<AllocationResponse> allocations) {
        public static PaymentResponse from(Payment payment) {
            BigDecimal allocated = payment.getAllocations() == null
                    ? BigDecimal.ZERO
                    : payment.getAllocations().stream()
                            .map(PaymentAllocation::getAllocatedAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal amount = payment.getAmount() == null ? BigDecimal.ZERO : payment.getAmount();
            BigDecimal unallocated = amount.subtract(allocated);
            if (unallocated.compareTo(BigDecimal.ZERO) < 0) {
                unallocated = BigDecimal.ZERO;
            }
            List<AllocationResponse> lines = payment.getAllocations() == null
                    ? List.of()
                    : payment.getAllocations().stream()
                            .map(a -> new AllocationResponse(
                                    a.getId(),
                                    a.getDocumentType(),
                                    a.getDocumentId(),
                                    a.getAllocatedAmount(),
                                    a.getCreatedAt()))
                            .toList();
            return new PaymentResponse(
                    payment.getId(),
                    payment.getPaymentNumber(),
                    payment.getPaymentDate(),
                    payment.getPaymentType(),
                    payment.getPartyType(),
                    payment.getCustomerId(),
                    payment.getSupplierId(),
                    payment.getFromAccountId(),
                    payment.getToAccountId(),
                    amount,
                    payment.getPaymentMode(),
                    payment.getTransactionReference(),
                    payment.getBankReference(),
                    payment.getNotes(),
                    payment.getStatus(),
                    allocated,
                    unallocated,
                    lines);
        }
    }
}
