package com.flowledger.ai.provider;

import java.util.List;

/** Stub provider — configure Claude API integration in a later phase. */
public class ClaudeProvider implements AIProvider {
    @Override
    public String name() {
        return "CLAUDE";
    }

    @Override
    public ChatResult chat(ChatRequest request) {
        throw new AiProviderException("Claude provider is not configured yet");
    }

    @Override
    public List<Float> embed(String text) {
        throw new AiProviderException("Claude embeddings are not configured yet");
    }
}
