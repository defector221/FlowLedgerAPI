package com.flowledger.commerce.publisher.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Normalized product projection emitted by CommercePublisher to all publish sinks. */
public record ProductPublishedSnapshot(
        UUID indexId,
        UUID organizationId,
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
        long version,
        String contentHash,
        Map<String, Object> payload) {}
