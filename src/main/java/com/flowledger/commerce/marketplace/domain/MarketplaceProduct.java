package com.flowledger.commerce.marketplace.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record MarketplaceProduct(
        UUID id,
        UUID storeId,
        UUID productId,
        String sku,
        String barcode,
        String gtin,
        String name,
        String description,
        String brand,
        UUID categoryId,
        String categoryName,
        BigDecimal price,
        String currency,
        BigDecimal inventoryQty,
        List<String> imageUrls,
        Map<String, Object> extras) {}
