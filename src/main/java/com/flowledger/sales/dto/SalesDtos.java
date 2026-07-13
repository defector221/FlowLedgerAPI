package com.flowledger.sales.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.*;
import java.time.*;
import java.util.*;

public final class SalesDtos {
    private SalesDtos() {}

    public record Item(
            @NotNull UUID productId,
            String description,
            String hsnSacCode,
            @NotNull @Positive BigDecimal quantity,
            UUID unitId,
            @NotNull @PositiveOrZero BigDecimal rate,
            BigDecimal discountPercent,
            BigDecimal taxRate) {}

    public record ChallanItem(
            @NotNull UUID productId,
            String description,
            @NotNull @Positive BigDecimal quantity,
            UUID unitId) {}

    public record ReturnItem(
            @NotNull UUID productId,
            @NotNull @Positive BigDecimal quantity,
            @NotNull @PositiveOrZero BigDecimal rate) {}

    public record Invoice(
            @NotNull UUID customerId,
            LocalDate invoiceDate,
            LocalDate dueDate,
            UUID warehouseId,
            UUID salesOrderId,
            UUID deliveryChallanId,
            String billingAddress,
            String shippingAddress,
            String placeOfSupply,
            Boolean taxInclusive,
            BigDecimal shippingCharges,
            BigDecimal additionalCharges,
            BigDecimal roundOff,
            String notes,
            String termsAndConditions,
            @NotEmpty @Valid List<Item> items) {}

    public record QuotationRequest(
            @NotNull UUID customerId,
            LocalDate quotationDate,
            LocalDate expiryDate,
            String billingAddress,
            String shippingAddress,
            String placeOfSupply,
            String notes,
            String termsAndConditions,
            @NotEmpty @Valid List<Item> items) {}

    public record OrderRequest(
            @NotNull UUID customerId,
            LocalDate orderDate,
            LocalDate expectedDeliveryDate,
            UUID quotationId,
            String billingAddress,
            String shippingAddress,
            String placeOfSupply,
            String notes,
            String termsAndConditions,
            @NotEmpty @Valid List<Item> items) {}

    public record ChallanRequest(
            @NotNull UUID customerId,
            LocalDate challanDate,
            UUID salesOrderId,
            UUID warehouseId,
            String notes,
            @NotEmpty @Valid List<ChallanItem> items) {}

    public record ReturnRequest(
            @NotNull UUID salesInvoiceId,
            LocalDate returnDate,
            String notes,
            @NotEmpty @Valid List<ReturnItem> items) {}

    public record CreditNoteRequest(
            @NotNull UUID customerId,
            UUID salesReturnId,
            UUID salesInvoiceId,
            LocalDate creditNoteDate,
            @NotNull @PositiveOrZero BigDecimal amount,
            String notes) {}

    public record InvoiceSummary(
            UUID id, String invoiceNumber, String status, BigDecimal grandTotal, BigDecimal outstanding) {}
}
