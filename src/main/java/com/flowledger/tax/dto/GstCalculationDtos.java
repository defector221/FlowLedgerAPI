package com.flowledger.tax.dto;

import jakarta.validation.constraints.*;
import java.math.*;

public final class GstCalculationDtos {
    private GstCalculationDtos() {}

    /**
     * @param taxType GST (default), IGST, or OTHER — controls split behaviour
     */
    public record Request(
            @NotBlank String organizationStateCode,
            @NotBlank String placeOfSupplyStateCode,
            @NotNull @DecimalMin("0.0") BigDecimal taxRate,
            @NotNull Boolean taxInclusive,
            @NotNull @DecimalMin("0.0") BigDecimal quantity,
            @NotNull @DecimalMin("0.0") BigDecimal rate,
            @DecimalMin("0.0") BigDecimal discount,
            String taxType) {
        public Request(
                String organizationStateCode,
                String placeOfSupplyStateCode,
                BigDecimal taxRate,
                Boolean taxInclusive,
                BigDecimal quantity,
                BigDecimal rate,
                BigDecimal discount) {
            this(organizationStateCode, placeOfSupplyStateCode, taxRate, taxInclusive, quantity, rate, discount, "GST");
        }
    }

    public record Response(
            BigDecimal taxable, BigDecimal cgst, BigDecimal sgst, BigDecimal igst, BigDecimal otherTax, BigDecimal lineTotal) {
        /** Back-compat helper when callers only need GST components. */
        public Response(BigDecimal taxable, BigDecimal cgst, BigDecimal sgst, BigDecimal igst, BigDecimal lineTotal) {
            this(taxable, cgst, sgst, igst, BigDecimal.ZERO, lineTotal);
        }
    }
}
