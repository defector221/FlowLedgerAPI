package com.flowledger.product.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class SupplierCatalogDtos {
    private SupplierCatalogDtos() {}

    public record Create(
            UUID productId,
            UUID supplierId,
            @Size(max = 255) String supplierSku,
            @Size(max = 255) String supplierProductName,
            @NotNull @DecimalMin("0.0") BigDecimal purchasePrice,
            @Size(min = 3, max = 3) String currency,
            @DecimalMin(value = "0.0", inclusive = false) BigDecimal moq,
            @PositiveOrZero Integer leadTimeDays,
            Boolean preferred,
            LocalDate validFrom,
            LocalDate validTo,
            String notes,
            Boolean active) {}

    public record Update(
            @Size(max = 255) String supplierSku,
            @Size(max = 255) String supplierProductName,
            @DecimalMin("0.0") BigDecimal purchasePrice,
            @Size(min = 3, max = 3) String currency,
            @DecimalMin(value = "0.0", inclusive = false) BigDecimal moq,
            @PositiveOrZero Integer leadTimeDays,
            Boolean preferred,
            LocalDate validFrom,
            LocalDate validTo,
            String notes,
            Boolean active) {}

    public record Response(
            UUID id,
            UUID productId,
            String productName,
            String productSku,
            String itemType,
            UUID taxRateId,
            UUID supplierId,
            String supplierName,
            String supplierSku,
            String supplierProductName,
            BigDecimal purchasePrice,
            String currency,
            BigDecimal moq,
            Integer leadTimeDays,
            boolean preferred,
            LocalDate validFrom,
            LocalDate validTo,
            String notes,
            boolean active,
            long version,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}
}
