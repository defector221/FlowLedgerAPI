package com.flowledger.demo.scenario;

import java.util.Locale;

public enum DemoScenario {
    RETAIL_SMALL("retail-small", "Retail World (Small)", "1 branch, 2 stores, ~500 products — fast local demo"),
    RETAIL_MEDIUM("retail-medium", "Retail World (Medium)", "5 branches, 20 stores, ~10k products"),
    RETAIL_ENTERPRISE("retail-enterprise", "Retail World (Enterprise)", "100 branches, 500 stores, ~100k products"),
    GROCERY_CHAIN("grocery-chain", "FreshMart Demo", "Grocery catalog with batch/expiry focus"),
    FASHION_CHAIN("fashion-chain", "StyleHub Demo", "Apparel with size/color variants"),
    ELECTRONICS_CHAIN("electronics-chain", "DigiMart Demo", "Serial-number and warranty tracking"),
    PHARMACY("pharmacy", "MedCare Pharmacy Demo", "Batch, expiry, FEFO-oriented stock"),
    WHOLESALE("wholesale", "TradeBulk Wholesale Demo", "B2B customers, credit limits, large POs"),
    OMNI_CHANNEL("omni-channel", "OmniRetail Demo", "Retail POS + ONLINE store + warehouse");

    private final String command;
    private final String organizationName;
    private final String description;

    DemoScenario(String command, String organizationName, String description) {
        this.command = command;
        this.organizationName = organizationName;
        this.description = description;
    }

    public String command() {
        return command;
    }

    public String organizationName() {
        return organizationName;
    }

    public String description() {
        return description;
    }

    public String slug() {
        return command;
    }

    public static DemoScenario fromCommand(String raw) {
        if (raw == null || raw.isBlank()) {
            return RETAIL_SMALL;
        }
        String key = raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
        for (DemoScenario s : values()) {
            if (s.command.equals(key) || s.name().equalsIgnoreCase(raw.trim())) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown demo scenario: " + raw);
    }
}
