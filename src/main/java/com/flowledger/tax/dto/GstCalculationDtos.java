package com.flowledger.tax.dto;

import jakarta.validation.constraints.*;
import java.math.*;

public final class GstCalculationDtos {
    private GstCalculationDtos() {}

    /**
     * @param taxType fallback when splitStrategy is null (GST / IGST / OTHER)
     * @param splitStrategy PLACE_OF_SUPPLY, NO_SPLIT_IGST, NO_SPLIT_OTHER, CUSTOM_PERCENT
     * @param cgstSharePercent share of total tax for CGST (default 50)
     * @param sgstSharePercent share of total tax for SGST (default 50)
     */
    public record Request(
            @NotBlank String organizationStateCode,
            @NotBlank String placeOfSupplyStateCode,
            @NotNull @DecimalMin("0.0") BigDecimal taxRate,
            @NotNull Boolean taxInclusive,
            @NotNull @DecimalMin("0.0") BigDecimal quantity,
            @NotNull @DecimalMin("0.0") BigDecimal rate,
            @DecimalMin("0.0") BigDecimal discount,
            String taxType,
            String splitStrategy,
            BigDecimal cgstSharePercent,
            BigDecimal sgstSharePercent) {

        public Request(
                String organizationStateCode,
                String placeOfSupplyStateCode,
                BigDecimal taxRate,
                Boolean taxInclusive,
                BigDecimal quantity,
                BigDecimal rate,
                BigDecimal discount) {
            this(
                    organizationStateCode,
                    placeOfSupplyStateCode,
                    taxRate,
                    taxInclusive,
                    quantity,
                    rate,
                    discount,
                    "GST",
                    null,
                    null,
                    null);
        }

        public Request(
                String organizationStateCode,
                String placeOfSupplyStateCode,
                BigDecimal taxRate,
                Boolean taxInclusive,
                BigDecimal quantity,
                BigDecimal rate,
                BigDecimal discount,
                String taxType) {
            this(
                    organizationStateCode,
                    placeOfSupplyStateCode,
                    taxRate,
                    taxInclusive,
                    quantity,
                    rate,
                    discount,
                    taxType,
                    null,
                    null,
                    null);
        }
    }

    public record Response(
            BigDecimal taxable, BigDecimal cgst, BigDecimal sgst, BigDecimal igst, BigDecimal otherTax, BigDecimal lineTotal) {
        public Response(BigDecimal taxable, BigDecimal cgst, BigDecimal sgst, BigDecimal igst, BigDecimal lineTotal) {
            this(taxable, cgst, sgst, igst, BigDecimal.ZERO, lineTotal);
        }
    }
}
