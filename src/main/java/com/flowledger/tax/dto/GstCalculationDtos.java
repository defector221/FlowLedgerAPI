package com.flowledger.tax.dto;

import jakarta.validation.constraints.*;
import java.math.*;

public final class GstCalculationDtos {
    private GstCalculationDtos() {}

    public record Request(
            @NotBlank String organizationStateCode,
            @NotBlank String placeOfSupplyStateCode,
            @NotNull @DecimalMin("0.0") BigDecimal taxRate,
            @NotNull Boolean taxInclusive,
            @NotNull @DecimalMin("0.0") BigDecimal quantity,
            @NotNull @DecimalMin("0.0") BigDecimal rate,
            @DecimalMin("0.0") BigDecimal discount) {}

    public record Response(
            BigDecimal taxable, BigDecimal cgst, BigDecimal sgst, BigDecimal igst, BigDecimal lineTotal) {}
}
