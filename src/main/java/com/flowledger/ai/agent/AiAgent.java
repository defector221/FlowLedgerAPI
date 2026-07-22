package com.flowledger.ai.agent;

import java.util.Set;

public interface AiAgent {
    AiAgentType type();

    String systemPromptTemplate();

    Set<String> allowedTools();
}
