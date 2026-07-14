package com.flowledger.product.dto;

import com.flowledger.product.entity.TaxType;
import jakarta.validation.constraints.*;
import java.math.*;
import java.util.*;

public final class TaxRateDtos {
    private TaxRateDtos() {}

    public record Create(
            @NotBlank String name,
            TaxType taxType,
            @NotNull @DecimalMin("0.0") BigDecimal rate,
            @DecimalMin("0.0") BigDecimal cessRate) {}

    public record Update(
            @NotBlank String name,
            TaxType taxType,
            @NotNull @DecimalMin("0.0") BigDecimal rate,
            @DecimalMin("0.0") BigDecimal cessRate,
            Boolean active) {}

    public record Response(
            UUID id,
            String name,
            TaxType taxType,
            BigDecimal rate,
            BigDecimal cgstRate,
            BigDecimal sgstRate,
            BigDecimal igstRate,
            BigDecimal cessRate,
            boolean active) {}
}
