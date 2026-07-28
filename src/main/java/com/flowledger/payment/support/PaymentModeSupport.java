package com.flowledger.payment.support;

import java.util.Locale;
import java.util.Set;

public final class PaymentModeSupport {
    public static final Set<String> LEDGER_MODES =
            Set.of("CASH", "BANK_TRANSFER", "UPI", "CHEQUE", "CREDIT_CARD", "DEBIT_CARD", "PAYMENT_GATEWAY", "OTHER");

    private PaymentModeSupport() {}

    /** Maps POS/UI aliases to values allowed by {@code chk_payment_mode}. */
    public static String normalize(String mode) {
        if (mode == null || mode.isBlank()) {
            return "OTHER";
        }
        String upper = mode.trim().toUpperCase(Locale.ROOT);
        if (LEDGER_MODES.contains(upper)) {
            return upper;
        }
        return switch (upper) {
            case "CARD" -> "CREDIT_CARD";
            case "WALLET" -> "PAYMENT_GATEWAY";
            case "CREDIT" -> "OTHER";
            default -> "OTHER";
        };
    }
}
