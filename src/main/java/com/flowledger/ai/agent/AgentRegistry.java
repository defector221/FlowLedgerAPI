package com.flowledger.ai.agent;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnAiEnabled
public class AgentRegistry {
    private final Map<AiAgentType, AiAgent> agents;

    public AgentRegistry(AgentFactory factory) {
        this.agents = factory.all();
    }

    public AiAgent get(AiAgentType type) {
        return agents.getOrDefault(type, agents.get(AiAgentType.CEO));
    }
}
