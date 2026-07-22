package com.flowledger.ai.agent;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public enum AiAgentType {
    ASK,
    BUSINESS_ADVISOR,
    CFO,
    ACCOUNTANT,
    INVENTORY_PLANNER,
    PROCUREMENT,
    GST_EXPERT,
    SALES_COACH,
    COLLECTIONS,
    CRM,
    CEO;

    private static final Map<String, AiAgentType> ALIASES = Map.ofEntries(
            Map.entry("FINANCE", CFO),
            Map.entry("ACCOUNTING", ACCOUNTANT),
            Map.entry("INVENTORY", INVENTORY_PLANNER),
            Map.entry("PURCHASE", PROCUREMENT),
            Map.entry("GST", GST_EXPERT),
            Map.entry("SALES", SALES_COACH),
            Map.entry("ASK_AGENT", ASK),
            Map.entry("GLOBAL_ASK", ASK));

    /** Default when agent is omitted: Global Ask Agent. */
    public static AiAgentType from(String value) {
        if (value == null || value.isBlank()) {
            return ASK;
        }
        String key = value.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        AiAgentType alias = ALIASES.get(key);
        if (alias != null) {
            return alias;
        }
        return AiAgentType.valueOf(key);
    }

    public static Optional<AiAgentType> tryFrom(String value) {
        try {
            return Optional.of(from(value));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public String promptTemplate() {
        return switch (this) {
            case ASK -> "agent-ask";
            case BUSINESS_ADVISOR -> "agent-business-advisor";
            case CFO -> "agent-cfo";
            case ACCOUNTANT -> "agent-accountant";
            case INVENTORY_PLANNER -> "agent-inventory-planner";
            case PROCUREMENT -> "agent-procurement";
            case GST_EXPERT -> "agent-gst-expert";
            case SALES_COACH -> "agent-sales-coach";
            case COLLECTIONS -> "agent-collections";
            case CRM -> "agent-crm";
            case CEO -> "agent-ceo";
        };
    }

    public String displayName() {
        return switch (this) {
            case ASK -> "Global Ask Agent";
            case BUSINESS_ADVISOR -> "AI Business Advisor";
            case CFO -> "AI CFO";
            case ACCOUNTANT -> "AI Accountant";
            case INVENTORY_PLANNER -> "AI Inventory Planner";
            case PROCUREMENT -> "AI Procurement Assistant";
            case GST_EXPERT -> "AI GST Expert";
            case SALES_COACH -> "AI Sales Coach";
            case COLLECTIONS -> "AI Collections Agent";
            case CRM -> "AI CRM";
            case CEO -> "CEO Orchestrator";
        };
    }

    public String description() {
        return switch (this) {
            case ASK -> "Routes questions and synthesizes multi-agent answers across the ERP.";
            case BUSINESS_ADVISOR -> "Strategic cross-domain recommendations for leadership.";
            case CFO -> "Cash flow, profitability, and budgeting insights.";
            case ACCOUNTANT -> "Journal suggestions and reconciliation guidance (advisory only).";
            case INVENTORY_PLANNER -> "Reorder optimization and stock risk planning.";
            case PROCUREMENT -> "Vendor comparison and purchase recommendations.";
            case GST_EXPERT -> "GST compliance checks and filing guidance.";
            case SALES_COACH -> "Customer and pipeline insights to improve revenue.";
            case COLLECTIONS -> "Payment follow-up priorities and collections scripts.";
            case CRM -> "Customer and supplier relationship context.";
            case CEO -> "Executive multi-agent orchestration across domains.";
        };
    }

    public String permissionHint() {
        return "AI_CHAT";
    }

    public boolean supportsCollaboration() {
        return this == ASK || this == CEO || this == BUSINESS_ADVISOR;
    }

    public static AiAgentType[] catalogOrder() {
        return Arrays.stream(values()).toArray(AiAgentType[]::new);
    }
}
