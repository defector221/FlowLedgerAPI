package com.flowledger.sales.dto;

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
            @NotEmpty List<Item> items) {}

    public record InvoiceSummary(
            UUID id, String invoiceNumber, String status, BigDecimal grandTotal, BigDecimal outstanding) {}
}
