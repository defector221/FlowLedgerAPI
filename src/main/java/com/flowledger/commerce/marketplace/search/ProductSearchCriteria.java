package com.flowledger.commerce.marketplace.search;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductSearchCriteria(
        String q,
        String barcode,
        String sku,
        String gtin,
        String productCode,
        String brand,
        UUID categoryId,
        UUID storeId,
        BigDecimal nearLat,
        BigDecimal nearLng,
        BigDecimal radiusKm) {}
