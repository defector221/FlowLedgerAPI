package com.flowledger.ai.agent;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAiEnabled
public class AgentFactory {
    public AiAgent create(AiAgentType type) {
        return switch (type) {
            case ASK ->
                new SimpleAiAgent(
                        type,
                        Set.of(
                                "dashboard",
                                "sales",
                                "inventory",
                                "payment",
                                "gst",
                                "report",
                                "customer",
                                "supplier",
                                "retail"));
            case BUSINESS_ADVISOR, CEO ->
                new SimpleAiAgent(
                        type, Set.of("dashboard", "sales", "inventory", "payment", "gst", "report", "accounting", "retail"));
            case CFO -> new SimpleAiAgent(type, Set.of("payment", "dashboard", "accounting", "report", "sales"));
            case ACCOUNTANT -> new SimpleAiAgent(type, Set.of("accounting", "payment", "report"));
            case INVENTORY_PLANNER -> new SimpleAiAgent(type, Set.of("inventory", "purchase", "dashboard", "supplier"));
            case PROCUREMENT -> new SimpleAiAgent(type, Set.of("purchase", "supplier", "inventory", "report"));
            case GST_EXPERT -> new SimpleAiAgent(type, Set.of("gst", "report", "sales", "purchase"));
            case SALES_COACH -> new SimpleAiAgent(type, Set.of("sales", "customer", "payment", "dashboard", "retail"));
            case COLLECTIONS -> new SimpleAiAgent(type, Set.of("payment", "customer", "sales", "dashboard"));
            case CRM -> new SimpleAiAgent(type, Set.of("customer", "supplier", "sales"));
        };
    }

    public Map<AiAgentType, AiAgent> all() {
        Map<AiAgentType, AiAgent> map = new EnumMap<>(AiAgentType.class);
        for (AiAgentType t : AiAgentType.values()) {
            map.put(t, create(t));
        }
        return map;
    }
}
