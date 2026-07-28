package com.flowledger.commerce.marketplace.search;

import java.math.BigDecimal;

public record GeoCriteria(BigDecimal lat, BigDecimal lng, BigDecimal radiusKm) {
    public boolean hasGeo() {
        return lat != null && lng != null && radiusKm != null && radiusKm.compareTo(BigDecimal.ZERO) > 0;
    }
}
