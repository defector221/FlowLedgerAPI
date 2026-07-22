package com.flowledger.ai.agent;

public enum AiAgentType {
    CEO,
    ACCOUNTING,
    INVENTORY,
    PURCHASE,
    SALES,
    GST,
    CRM,
    FINANCE;

    public static AiAgentType from(String value) {
        if (value == null || value.isBlank()) {
            return CEO;
        }
        return AiAgentType.valueOf(value.trim().toUpperCase());
    }

    public String promptTemplate() {
        return switch (this) {
            case CEO -> "agent-ceo";
            case ACCOUNTING -> "agent-accounting";
            case INVENTORY -> "agent-inventory";
            case PURCHASE -> "agent-purchase";
            case SALES -> "agent-sales";
            case GST -> "agent-gst";
            case CRM -> "agent-crm";
            case FINANCE -> "agent-finance";
        };
    }
}
