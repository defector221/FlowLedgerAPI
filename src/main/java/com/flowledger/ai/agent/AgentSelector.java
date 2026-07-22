package com.flowledger.ai.agent;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAiEnabled
public class AgentSelector {
    private final AgentRegistry registry;

    public AgentSelector(AgentRegistry registry) {
        this.registry = registry;
    }

    /**
     * Explicit agent field wins (with aliases). Blank agent → Global Ask ({@link AiAgentType#ASK}).
     * Keyword routing only when agent is omitted and Ask collaboration will still consult specialists.
     */
    public AiAgent select(String agentField, String message) {
        if (agentField != null && !agentField.isBlank()) {
            return AiAgentType.tryFrom(agentField).map(registry::get).orElseGet(() -> registry.get(AiAgentType.ASK));
        }
        // Default product behavior: Global Ask Agent (multi-agent capable)
        return registry.get(AiAgentType.ASK);
    }

    /** Keyword → specialist for multi-agent fan-out (not for primary selection when Ask is default). */
    public AiAgentType suggestSpecialist(String message) {
        String m = message == null ? "" : message.toLowerCase();
        if (containsAny(m, "gst", "tax", "cgst", "sgst", "igst", "hsn")) {
            return AiAgentType.GST_EXPERT;
        }
        if (containsAny(m, "collect", "overdue", "follow-up", "follow up", "dunning", "aging")) {
            return AiAgentType.COLLECTIONS;
        }
        if (containsAny(m, "stock", "inventory", "reorder", "warehouse", "sku")) {
            return AiAgentType.INVENTORY_PLANNER;
        }
        if (containsAny(m, "purchase", "po ", "supplier", "vendor", "procurement", "grn")) {
            return AiAgentType.PROCUREMENT;
        }
        if (containsAny(m, "pipeline", "coach", "upsell", "cross-sell", "quota")) {
            return AiAgentType.SALES_COACH;
        }
        if (containsAny(m, "invoice", "sales", "revenue", "quotation")) {
            return AiAgentType.SALES_COACH;
        }
        if (containsAny(m, "journal", "reconcile", "reconciliation", "trial balance", "ledger", "p&l")) {
            return AiAgentType.ACCOUNTANT;
        }
        if (containsAny(m, "cash", "budget", "profit", "cfo", "working capital", "receivable", "payable")) {
            return AiAgentType.CFO;
        }
        if (containsAny(m, "customer", "crm", "lead")) {
            return AiAgentType.CRM;
        }
        return AiAgentType.BUSINESS_ADVISOR;
    }

    private static boolean containsAny(String text, String... keys) {
        for (String k : keys) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }
}
