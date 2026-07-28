package com.flowledger.tax.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class TaxCalculationDtos {
    private TaxCalculationDtos() {}

    public record TaxCalculationRequest(
            String organizationStateCode,
            String placeOfSupplyStateCode,
            String countryCode,
            LocalDate effectiveDate,
            UUID productId,
            UUID productCategoryId,
            UUID taxCategoryId,
            BigDecimal quantity,
            BigDecimal rate,
            BigDecimal discount,
            Boolean taxInclusive,
            String taxType,
            String splitStrategy,
            BigDecimal cgstSharePercent,
            BigDecimal sgstSharePercent) {}

    public record LineTaxRequest(
            @NotNull @DecimalMin("0.0") BigDecimal quantity,
            @NotNull @DecimalMin("0.0") BigDecimal rate,
            @DecimalMin("0.0") BigDecimal discount,
            UUID productId,
            UUID productCategoryId,
            UUID taxCategoryId,
            String taxType,
            String splitStrategy,
            BigDecimal cgstSharePercent,
            BigDecimal sgstSharePercent) {}

    public record DocumentTaxRequest(
            @NotBlank String organizationStateCode,
            @NotBlank String placeOfSupplyStateCode,
            String countryCode,
            LocalDate effectiveDate,
            @NotNull List<LineTaxRequest> lines) {}

    public record RuleLookupRequest(
            UUID productId,
            UUID productCategoryId,
            UUID taxCategoryId,
            @NotBlank String countryCode,
            String stateCode,
            @NotNull LocalDate effectiveDate) {}

    public record TaxBreakdownLine(String component, BigDecimal rate, BigDecimal amount) {}

    public record TaxBreakdown(
            BigDecimal taxableAmount, BigDecimal totalTax, BigDecimal cessAmount, List<TaxBreakdownLine> lines) {}

    public record TaxResult(
            UUID taxCategoryId,
            String taxCategoryCode,
            UUID taxRuleId,
            String taxRuleVersion,
            BigDecimal taxableAmount,
            BigDecimal cgstAmount,
            BigDecimal sgstAmount,
            BigDecimal igstAmount,
            BigDecimal cessAmount,
            BigDecimal otherTaxAmount,
            BigDecimal lineTotal,
            TaxBreakdown breakdown,
            boolean interState,
            boolean intraState) {}

    public record TaxSummary(
            BigDecimal taxableTotal,
            BigDecimal cgstTotal,
            BigDecimal sgstTotal,
            BigDecimal igstTotal,
            BigDecimal cessTotal,
            BigDecimal otherTaxTotal,
            BigDecimal documentTotal,
            List<TaxResult> lines) {}
}
