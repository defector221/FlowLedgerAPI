package com.flowledger.common.util;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MergeTags {
    private static final Pattern TAG = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*\\}\\}");

    private MergeTags() {}

    public static String apply(String template, Map<String, String> values) {
        if (template == null || template.isBlank()) {
            return template == null ? "" : template;
        }
        if (values == null || values.isEmpty()) {
            return template;
        }
        Matcher matcher = TAG.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = values.getOrDefault(key, "");
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement == null ? "" : replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    public static Map<String, String> sampleLeadTags() {
        return Map.of(
                "firstName",
                "Asha",
                "lastName",
                "Patel",
                "leadName",
                "Asha Patel",
                "company",
                "Acme Traders",
                "email",
                "asha@example.com",
                "phone",
                "+91 98765 43210");
    }

    public static Map<String, String> sampleCustomerTags() {
        return Map.of(
                "firstName",
                "Ravi",
                "customerName",
                "Ravi Kumar",
                "company",
                "Kumar Electronics",
                "email",
                "ravi@example.com",
                "phone",
                "+91 99887 76655");
    }

    public static Map<String, String> sampleInvoiceTags() {
        return Map.of(
                "invoiceNumber",
                "INV-2026-001",
                "customerName",
                "Acme Traders",
                "invoiceDate",
                "13-07-2026",
                "grandTotal",
                "11,800.00",
                "organizationName",
                "FlowLedger Demo",
                "gstin",
                "27AAAAA0000A1Z5");
    }
}
