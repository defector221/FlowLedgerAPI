package com.flowledger.migration.writer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class WriterSupport {
    private static final List<DateTimeFormatter> DATES = List.of(
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH));

    private WriterSupport() {}

    static String str(Map<String, String> row, String key) {
        String v = row.get(key);
        return v == null || v.isBlank() ? null : v.trim();
    }

    static String required(Map<String, String> row, String key) {
        String v = str(row, key);
        if (v == null) throw new IllegalArgumentException(key + " is required");
        return v;
    }

    static BigDecimal decimal(Map<String, String> row, String key) {
        String v = str(row, key);
        if (v == null) return null;
        try {
            return new BigDecimal(v.replace(",", ""));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(key + " must be a number");
        }
    }

    static BigDecimal decimalOrZero(Map<String, String> row, String key) {
        BigDecimal v = decimal(row, key);
        return v == null ? BigDecimal.ZERO : v;
    }

    static LocalDate date(Map<String, String> row, String key) {
        String v = str(row, key);
        if (v == null) return null;
        for (DateTimeFormatter f : DATES) {
            try {
                return LocalDate.parse(v, f);
            } catch (DateTimeParseException ignored) {
            }
        }
        throw new IllegalArgumentException(key + " has invalid date: " + v);
    }

    static LocalDate dateOrToday(Map<String, String> row, String key) {
        LocalDate d = date(row, key);
        return d == null ? LocalDate.now() : d;
    }

    static boolean bool(Map<String, String> row, String key) {
        String v = str(row, key);
        if (v == null) return false;
        return v.equalsIgnoreCase("true")
                || v.equalsIgnoreCase("yes")
                || v.equalsIgnoreCase("y")
                || v.equals("1");
    }
}
