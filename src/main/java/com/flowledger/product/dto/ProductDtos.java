package com.flowledger.product.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public final class ProductDtos {
    private ProductDtos() {}

    public record Create(
            String sku,
            @NotBlank String name,
            @NotNull UUID unitId,
            String itemType,
            String barcode,
            String description,
            UUID categoryId,
            String brand,
            String hsnSacCode,
            @DecimalMin("0.0") BigDecimal purchasePrice,
            @DecimalMin("0.0") BigDecimal sellingPrice,
            @DecimalMin("0.0") BigDecimal mrp,
            UUID taxRateId,
            @DecimalMin("0.0") BigDecimal openingStock,
            @DecimalMin("0.0") BigDecimal minimumStockLevel,
            BigDecimal maximumStockLevel,
            @DecimalMin("0.0") BigDecimal reorderLevel,
            Boolean batchTracking,
            Boolean serialTracking,
            Boolean expiryTracking) {}

    public record Update(
            @NotBlank String name,
            String barcode,
            String description,
            UUID categoryId,
            String brand,
            String hsnSacCode,
            UUID unitId,
            @DecimalMin("0.0") BigDecimal purchasePrice,
            @DecimalMin("0.0") BigDecimal sellingPrice,
            @DecimalMin("0.0") BigDecimal mrp,
            UUID taxRateId,
            @DecimalMin("0.0") BigDecimal minimumStockLevel,
            BigDecimal maximumStockLevel,
            @DecimalMin("0.0") BigDecimal reorderLevel,
            Boolean batchTracking,
            Boolean serialTracking,
            Boolean expiryTracking,
            Boolean active) {}

    public record Response(
            UUID id,
            String itemType,
            String sku,
            String barcode,
            String name,
            String description,
            UUID categoryId,
            String categoryName,
            String brand,
            String hsnSacCode,
            UUID unitId,
            String unitName,
            BigDecimal purchasePrice,
            BigDecimal sellingPrice,
            BigDecimal mrp,
            UUID taxRateId,
            String taxRateName,
            String taxType,
            BigDecimal minimumStockLevel,
            BigDecimal reorderLevel,
            boolean active) {}

    public record Search(String search, Boolean active) {}
}
