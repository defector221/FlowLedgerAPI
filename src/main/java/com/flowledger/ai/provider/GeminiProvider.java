package com.flowledger.ai.provider;

import java.util.List;

/** Stub provider — configure Gemini API integration in a later phase. */
public class GeminiProvider implements AIProvider {
    @Override
    public String name() {
        return "GEMINI";
    }

    @Override
    public ChatResult chat(ChatRequest request) {
        throw new AiProviderException("Gemini provider is not configured yet");
    }

    @Override
    public List<Float> embed(String text) {
        throw new AiProviderException("Gemini embeddings are not configured yet");
    }
}
