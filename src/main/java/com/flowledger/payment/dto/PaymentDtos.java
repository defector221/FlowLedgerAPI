package com.flowledger.payment.dto;

import com.flowledger.payment.entity.Payment;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
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
}
