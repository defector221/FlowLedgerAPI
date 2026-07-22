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
            case CEO -> new SimpleAiAgent(type, Set.of("dashboard", "sales", "inventory", "payment", "gst", "report"));
            case ACCOUNTING -> new SimpleAiAgent(type, Set.of("accounting", "payment", "report"));
            case INVENTORY -> new SimpleAiAgent(type, Set.of("inventory", "purchase", "dashboard"));
            case PURCHASE -> new SimpleAiAgent(type, Set.of("purchase", "supplier", "inventory"));
            case SALES -> new SimpleAiAgent(type, Set.of("sales", "customer", "payment", "dashboard"));
            case GST -> new SimpleAiAgent(type, Set.of("gst", "report", "sales", "purchase"));
            case CRM -> new SimpleAiAgent(type, Set.of("customer", "supplier", "sales"));
            case FINANCE -> new SimpleAiAgent(type, Set.of("payment", "dashboard", "accounting", "report"));
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
