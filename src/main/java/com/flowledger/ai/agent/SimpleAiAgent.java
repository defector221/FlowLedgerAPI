package com.flowledger.ai.agent;

import java.util.Set;

record SimpleAiAgent(AiAgentType type, Set<String> allowedTools) implements AiAgent {
    @Override
    public String systemPromptTemplate() {
        return type.promptTemplate();
    }
}
