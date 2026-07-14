package com.flowledger.purchase.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class PurchaseDtos {
    private PurchaseDtos() {}

    public record Line(
            @NotNull UUID productId,
            UUID unitId,
            String description,
            String hsnSacCode,
            @NotNull @DecimalMin("0.0001") BigDecimal quantity,
            @NotNull @DecimalMin("0") BigDecimal rate,
            BigDecimal discountPercent,
            BigDecimal taxRate,
            String taxType) {}

    public record OrderRequest(
            @NotNull UUID supplierId,
            @NotNull LocalDate orderDate,
            LocalDate expectedDeliveryDate,
            String notes,
            String termsAndConditions,
            @NotEmpty List<@Valid Line> items) {}

    public record GrnRequest(
            @NotNull UUID warehouseId, @NotNull LocalDate receiptDate, String notes, List<@Valid Line> items) {}

    public record InvoiceRequest(
            String supplierInvoiceNumber,
            @NotNull LocalDate invoiceDate,
            LocalDate dueDate,
            String placeOfSupply,
            Boolean taxInclusive,
            String notes,
            List<@Valid Line> items) {}

    public record ReturnRequest(
            @NotNull UUID purchaseInvoiceId,
            @NotNull LocalDate returnDate,
            String notes,
            @NotEmpty List<@Valid Line> items) {}

    public record DebitNoteRequest(
            UUID purchaseReturnId,
            UUID purchaseInvoiceId,
            UUID supplierId,
            LocalDate debitNoteDate,
            BigDecimal amount,
            String notes) {}
}
