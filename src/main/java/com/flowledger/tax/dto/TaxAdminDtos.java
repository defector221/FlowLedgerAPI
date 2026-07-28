package com.flowledger.tax.dto;

import com.flowledger.product.entity.TaxType;
import com.flowledger.tax.domain.JurisdictionType;
import com.flowledger.tax.domain.TaxProviderCode;
import com.flowledger.tax.dto.TaxCalculationDtos.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class TaxAdminDtos {
    private TaxAdminDtos() {}

    public record CategoryRequest(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 200) String name,
            String description,
            @NotBlank @Size(max = 3) String countryCode,
            Boolean active) {}

    public record CategoryResponse(
            UUID id,
            String code,
            String name,
            String description,
            String countryCode,
            boolean active,
            boolean systemDefined) {}

    public record RuleRequest(
            @NotNull UUID taxCategoryId,
            TaxType taxType,
            @NotBlank @Size(max = 3) String countryCode,
            String stateCode,
            @NotNull @DecimalMin("0.0") BigDecimal cgstRate,
            @NotNull @DecimalMin("0.0") BigDecimal sgstRate,
            @NotNull @DecimalMin("0.0") BigDecimal igstRate,
            @DecimalMin("0.0") BigDecimal cessRate,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            Boolean inclusive,
            Integer priority,
            Boolean reverseCharge,
            Boolean zeroRated,
            Boolean exempt,
            Boolean nilRated,
            String versionLabel) {}

    public record RuleResponse(
            UUID id,
            UUID taxCategoryId,
            TaxType taxType,
            String countryCode,
            String stateCode,
            BigDecimal cgstRate,
            BigDecimal sgstRate,
            BigDecimal igstRate,
            BigDecimal cessRate,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean inclusive,
            int priority,
            boolean reverseCharge,
            boolean zeroRated,
            boolean exempt,
            boolean nilRated,
            boolean active,
            String versionLabel,
            UUID supersededBy) {}

    public record JurisdictionRequest(
            @NotBlank @Size(max = 3) String country,
            String state,
            @NotBlank @Size(max = 20) String code,
            @NotNull JurisdictionType type,
            Boolean active) {}

    public record JurisdictionResponse(
            UUID id, String country, String state, String code, JurisdictionType type, boolean active) {}

    public record HsnSacRequest(
            @NotBlank @Size(max = 20) String code,
            @Size(max = 500) String description,
            @NotBlank @Pattern(regexp = "HSN|SAC") String type,
            Boolean active) {}

    public record HsnSacResponse(UUID id, String code, String description, String type, boolean active) {}

    public record SettingsRequest(
            UUID defaultTaxCategoryId, TaxProviderCode providerCode, Integer roundingScale, String roundingMode) {}

    public record SettingsResponse(
            UUID organizationId,
            UUID defaultTaxCategoryId,
            TaxProviderCode providerCode,
            int roundingScale,
            String roundingMode) {}

    public record SimulateRequest(
            @NotBlank String organizationStateCode,
            @NotBlank String placeOfSupplyStateCode,
            String countryCode,
            LocalDate effectiveDate,
            @NotNull LineTaxRequest line) {}
}
