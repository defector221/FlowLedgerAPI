package com.flowledger.product.entity;

/** Behaviour of a tax master when applied on documents. */
public enum TaxType {
    /** Standard GST: intra = CGST+SGST, inter = IGST. */
    GST,
    /** Always IGST at full rate; never split. */
    IGST,
    /** Non-GST / flat tax: apply rate as marked with no CGST/SGST/IGST split. */
    OTHER;

    public static TaxType from(String value) {
        if (value == null || value.isBlank()) {
            return GST;
        }
        try {
            return TaxType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return GST;
        }
    }
}
