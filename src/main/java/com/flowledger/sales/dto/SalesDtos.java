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
            BigDecimal taxRate,
            String taxType,
            String splitStrategy,
            BigDecimal cgstSharePercent,
            BigDecimal sgstSharePercent) {}

    public record ChallanItem(
            @NotNull UUID productId, String description, @NotNull @Positive BigDecimal quantity, UUID unitId) {}

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
            UUID templateId,
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
            Boolean transportRequired,
            @NotEmpty @Valid List<ChallanItem> items) {}

    public record TransportRequiredRequest(@NotNull Boolean transportRequired) {}

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

    public record InvoiceItemDetail(
            UUID id,
            UUID productId,
            String productName,
            String itemType,
            String unitName,
            String description,
            String hsnSacCode,
            BigDecimal quantity,
            BigDecimal rate,
            BigDecimal discountPercent,
            BigDecimal discountAmount,
            BigDecimal taxRate,
            String taxType,
            String splitStrategy,
            BigDecimal cgstSharePercent,
            BigDecimal sgstSharePercent,
            BigDecimal lineTotal,
            String warehouseName) {}

    public record InvoiceDetail(
            UUID id,
            String invoiceNumber,
            LocalDate invoiceDate,
            LocalDate dueDate,
            UUID customerId,
            UUID warehouseId,
            String warehouseName,
            String status,
            String paymentStatus,
            BigDecimal subtotal,
            BigDecimal discountTotal,
            BigDecimal taxableAmount,
            BigDecimal cgstTotal,
            BigDecimal sgstTotal,
            BigDecimal igstTotal,
            BigDecimal shippingCharges,
            BigDecimal additionalCharges,
            BigDecimal roundOff,
            BigDecimal grandTotal,
            BigDecimal amountPaid,
            BigDecimal outstandingAmount,
            String notes,
            String termsAndConditions,
            UUID templateId,
            String billingAddress,
            String shippingAddress,
            String placeOfSupply,
            UUID salesOrderId,
            UUID deliveryChallanId,
            OffsetDateTime createdAt,
            List<InvoiceItemDetail> items) {}
}
