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

    public AiAgent select(String agentField, String message) {
        if (agentField != null && !agentField.isBlank()) {
            try {
                return registry.get(AiAgentType.from(agentField));
            } catch (IllegalArgumentException ignored) {
                // fall through to keyword selection
            }
        }
        String m = message == null ? "" : message.toLowerCase();
        if (containsAny(m, "gst", "tax", "cgst", "sgst", "igst")) {
            return registry.get(AiAgentType.GST);
        }
        if (containsAny(m, "stock", "inventory", "reorder", "warehouse")) {
            return registry.get(AiAgentType.INVENTORY);
        }
        if (containsAny(m, "purchase", "po ", "supplier bill", "grn")) {
            return registry.get(AiAgentType.PURCHASE);
        }
        if (containsAny(m, "invoice", "sales", "revenue", "customer invoice")) {
            return registry.get(AiAgentType.SALES);
        }
        if (containsAny(m, "ledger", "journal", "trial balance", "p&l", "balance sheet")) {
            return registry.get(AiAgentType.ACCOUNTING);
        }
        if (containsAny(m, "payment", "receivable", "payable", "cashflow", "cash flow")) {
            return registry.get(AiAgentType.FINANCE);
        }
        if (containsAny(m, "customer", "supplier", "crm")) {
            return registry.get(AiAgentType.CRM);
        }
        return registry.get(AiAgentType.CEO);
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
