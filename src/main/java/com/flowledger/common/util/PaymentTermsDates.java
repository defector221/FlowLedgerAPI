package com.flowledger.common.util;

import java.time.LocalDate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Shared payment-terms parsing for sales/purchase due dates. */
public final class PaymentTermsDates {
    private static final Pattern PAYMENT_TERMS_DAYS = Pattern.compile("(\\d{1,3})");
    private static final int DEFAULT_DAYS = 30;

    private PaymentTermsDates() {}

    public static int parseDays(String paymentTerms) {
        if (paymentTerms == null || paymentTerms.isBlank()) return DEFAULT_DAYS;
        Matcher matcher = PAYMENT_TERMS_DAYS.matcher(paymentTerms.trim());
        if (matcher.find()) {
            try {
                int days = Integer.parseInt(matcher.group(1));
                if (days >= 0 && days <= 365) return days;
            } catch (NumberFormatException ignored) {
                // fall through
            }
        }
        return DEFAULT_DAYS;
    }

    public static LocalDate dueDate(LocalDate invoiceDate, String paymentTerms) {
        LocalDate base = invoiceDate != null ? invoiceDate : LocalDate.now();
        return base.plusDays(parseDays(paymentTerms));
    }
}
