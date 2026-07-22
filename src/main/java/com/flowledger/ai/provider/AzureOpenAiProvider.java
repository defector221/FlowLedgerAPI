package com.flowledger.ai.provider;

import java.util.List;

/** Stub provider — configure Azure OpenAI integration in a later phase. */
public class AzureOpenAiProvider implements AIProvider {
    @Override
    public String name() {
        return "AZURE_OPENAI";
    }

    @Override
    public ChatResult chat(ChatRequest request) {
        throw new AiProviderException("Azure OpenAI provider is not configured yet");
    }

    @Override
    public List<Float> embed(String text) {
        throw new AiProviderException("Azure OpenAI embeddings are not configured yet");
    }
}
