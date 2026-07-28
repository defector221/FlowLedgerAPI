package com.flowledger.commerce.publisher.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Normalized store projection emitted by CommercePublisher to all publish sinks. */
public record StorePublishedSnapshot(
        UUID organizationId,
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
        Map<String, Object> payload) {}
