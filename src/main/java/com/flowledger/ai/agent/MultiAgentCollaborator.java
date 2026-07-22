package com.flowledger.ai.agent;

import com.flowledger.ai.config.AiProperties;
import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.tools.AiToolRegistry;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Fan-out read-only specialist consults for Ask / CEO / Business Advisor, then return notes for synthesis.
 */
@Service
@ConditionalOnAiEnabled
public class MultiAgentCollaborator {
    private static final int MAX_SPECIALISTS = 3;

    private final AiProperties properties;
    private final AgentSelector selector;
    private final AgentRegistry registry;
    private final AiToolRegistry tools;

    public MultiAgentCollaborator(
            AiProperties properties, AgentSelector selector, AgentRegistry registry, AiToolRegistry tools) {
        this.properties = properties;
        this.selector = selector;
        this.registry = registry;
        this.tools = tools;
    }

    public record ConsultResult(List<String> consultedAgents, String specialistNotes) {}

    public ConsultResult consult(AiAgentType primary, String message) {
        if (!properties.isMultiAgentEnabled() || !primary.supportsCollaboration()) {
            return new ConsultResult(List.of(), "");
        }

        List<AiAgentType> specialists = pickSpecialists(message);
        if (specialists.isEmpty()) {
            specialists = List.of(AiAgentType.CFO, AiAgentType.INVENTORY_PLANNER, AiAgentType.SALES_COACH);
        }

        StringBuilder notes = new StringBuilder();
        List<String> consulted = new ArrayList<>();
        for (AiAgentType type : specialists) {
            if (type == primary) {
                continue;
            }
            AiAgent agent = registry.get(type);
            String toolData = tools.invokeAllowed(agent.allowedTools(), message);
            consulted.add(type.name());
            notes.append("### ")
                    .append(type.displayName())
                    .append(" (")
                    .append(type.name())
                    .append(")\n")
                    .append(toolData == null || toolData.isBlank() ? "(no tool data)" : toolData)
                    .append("\n\n");
            if (consulted.size() >= MAX_SPECIALISTS) {
                break;
            }
        }
        return new ConsultResult(consulted, notes.toString().trim());
    }

    private List<AiAgentType> pickSpecialists(String message) {
        LinkedHashSet<AiAgentType> picked = new LinkedHashSet<>();
        String m = message == null ? "" : message.toLowerCase(Locale.ROOT);

        // Always include the keyword-primary specialist first
        picked.add(selector.suggestSpecialist(message));

        if (containsAny(m, "cash", "profit", "budget", "receivable", "payable", "working capital")) {
            picked.add(AiAgentType.CFO);
        }
        if (containsAny(m, "stock", "inventory", "reorder")) {
            picked.add(AiAgentType.INVENTORY_PLANNER);
        }
        if (containsAny(m, "gst", "tax")) {
            picked.add(AiAgentType.GST_EXPERT);
        }
        if (containsAny(m, "purchase", "vendor", "supplier", "procurement")) {
            picked.add(AiAgentType.PROCUREMENT);
        }
        if (containsAny(m, "overdue", "collect", "aging")) {
            picked.add(AiAgentType.COLLECTIONS);
        }
        if (containsAny(m, "sales", "customer", "pipeline", "revenue")) {
            picked.add(AiAgentType.SALES_COACH);
        }
        if (containsAny(m, "journal", "reconcile", "ledger")) {
            picked.add(AiAgentType.ACCOUNTANT);
        }

        // Broad business questions: ensure finance + ops coverage
        if (picked.size() < 2) {
            picked.add(AiAgentType.CFO);
            picked.add(AiAgentType.INVENTORY_PLANNER);
        }

        List<AiAgentType> list = new ArrayList<>(picked);
        if (list.size() > MAX_SPECIALISTS) {
            return list.subList(0, MAX_SPECIALISTS);
        }
        return list;
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
