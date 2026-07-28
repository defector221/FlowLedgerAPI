package com.flowledger.tax.domain;

public enum TaxProviderCode {
    IndiaGST,
    EuropeVAT,
    USSalesTax;

    public static TaxProviderCode from(String value) {
        if (value == null || value.isBlank()) {
            return IndiaGST;
        }
        try {
            return TaxProviderCode.valueOf(value.trim());
        } catch (IllegalArgumentException ex) {
            return IndiaGST;
        }
    }
}
