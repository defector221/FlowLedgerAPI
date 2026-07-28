package com.flowledger.tax.domain;

public enum JurisdictionType {
    GST,
    VAT,
    SalesTax;

    public static JurisdictionType from(String value) {
        if (value == null || value.isBlank()) {
            return GST;
        }
        try {
            return JurisdictionType.valueOf(value.trim());
        } catch (IllegalArgumentException ex) {
            return GST;
        }
    }
}
