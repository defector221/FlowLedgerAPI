package com.flowledger.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class AmountInWords {
    private static final String[] ONES = {
        "",
        "One",
        "Two",
        "Three",
        "Four",
        "Five",
        "Six",
        "Seven",
        "Eight",
        "Nine",
        "Ten",
        "Eleven",
        "Twelve",
        "Thirteen",
        "Fourteen",
        "Fifteen",
        "Sixteen",
        "Seventeen",
        "Eighteen",
        "Nineteen"
    };
    private static final String[] TENS = {
        "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
    };

    private AmountInWords() {}

    public static String inr(BigDecimal amount) {
        BigDecimal v = amount.abs().setScale(2, RoundingMode.HALF_UP);
        long rupees = v.longValue();
        int paise = v.remainder(BigDecimal.ONE).movePointRight(2).intValue();
        String result = (amount.signum() < 0 ? "Minus " : "") + words(rupees) + " Rupees"
                + (paise > 0 ? " and " + words(paise) + " Paise" : "") + " Only";
        return result.replaceAll("\\s+", " ").trim();
    }

    private static String words(long n) {
        if (n == 0) return "Zero";
        if (n >= 10_000_000) return words(n / 10_000_000) + " Crore " + words(n % 10_000_000);
        if (n >= 100_000) return words(n / 100_000) + " Lakh " + words(n % 100_000);
        if (n >= 1_000) return words(n / 1_000) + " Thousand " + words(n % 1_000);
        if (n >= 100) return ONES[(int) (n / 100)] + " Hundred " + words(n % 100);
        return n < 20 ? ONES[(int) n] : TENS[(int) (n / 10)] + (n % 10 == 0 ? "" : " " + ONES[(int) (n % 10)]);
    }
}
