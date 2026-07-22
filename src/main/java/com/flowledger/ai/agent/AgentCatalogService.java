package com.flowledger.ai.agent;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.dto.AiDtos;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnAiEnabled
public class AgentCatalogService {
    private final AgentRegistry registry;

    public AgentCatalogService(AgentRegistry registry) {
        this.registry = registry;
    }

    public List<AiDtos.AgentInfo> list() {
        return Arrays.stream(AiAgentType.catalogOrder())
                .map(t -> {
                    AiAgent agent = registry.get(t);
                    return new AiDtos.AgentInfo(
                            t.name(),
                            t.displayName(),
                            t.description(),
                            agent.allowedTools().stream().sorted().toList(),
                            t.permissionHint(),
                            t.supportsCollaboration());
                })
                .toList();
    }
}
