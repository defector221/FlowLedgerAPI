package com.flowledger.tax.domain;

public enum PricingMode {
    INCLUSIVE,
    EXCLUSIVE;

    public static PricingMode from(Boolean inclusive) {
        return Boolean.TRUE.equals(inclusive) ? INCLUSIVE : EXCLUSIVE;
    }

    public static PricingMode from(String value) {
        if (value == null || value.isBlank()) {
            return EXCLUSIVE;
        }
        try {
            return PricingMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return EXCLUSIVE;
        }
    }
}
