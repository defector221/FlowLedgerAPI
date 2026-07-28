package com.flowledger.commerce.marketplace.domain;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record MarketplaceStore(
        UUID id,
        UUID storeId,
        String name,
        String city,
        String postalCode,
        String state,
        String country,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal discoveryRadiusKm,
        String visibility,
        boolean supportsDelivery,
        boolean supportsPickup,
        boolean supportsClickCollect,
        boolean supportsScanAndGo,
        Double distanceKm,
        Map<String, Object> extras) {}
