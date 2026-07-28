package com.flowledger.commerce.marketplace.search;

import java.math.BigDecimal;
import java.util.UUID;

public record StoreSearchCriteria(
        String q,
        String city,
        String pincode,
        BigDecimal lat,
        BigDecimal lng,
        BigDecimal radiusKm,
        Boolean supportsDelivery,
        Boolean supportsPickup,
        Boolean supportsClickCollect) {}
