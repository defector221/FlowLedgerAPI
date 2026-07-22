package com.flowledger.ai.provider;

import java.util.List;

/** Stub provider — configure local Ollama integration in a later phase. */
public class OllamaProvider implements AIProvider {
    @Override
    public String name() {
        return "OLLAMA";
    }

    @Override
    public ChatResult chat(ChatRequest request) {
        throw new AiProviderException("Ollama provider is not configured yet");
    }

    @Override
    public List<Float> embed(String text) {
        throw new AiProviderException("Ollama embeddings are not configured yet");
    }
}
