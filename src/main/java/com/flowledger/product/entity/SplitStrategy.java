package com.flowledger.product.entity;

/** How a tax rate distributes its total tax across components. */
public enum SplitStrategy {
    /** Intra-state: CGST/SGST by shares; inter-state: full IGST. */
    PLACE_OF_SUPPLY,
    /** Always full rate as IGST. */
    NO_SPLIT_IGST,
    /** Always flat non-GST tax (otherTax). */
    NO_SPLIT_OTHER,
    /** Always CGST/SGST by shares; ignore place of supply. */
    CUSTOM_PERCENT;

    public static SplitStrategy from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return SplitStrategy.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static SplitStrategy defaultFor(TaxType taxType) {
        TaxType type = taxType == null ? TaxType.GST : taxType;
        return switch (type) {
            case IGST -> NO_SPLIT_IGST;
            case OTHER -> NO_SPLIT_OTHER;
            case GST -> PLACE_OF_SUPPLY;
        };
    }
}
