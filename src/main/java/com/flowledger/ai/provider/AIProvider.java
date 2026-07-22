package com.flowledger.ai.provider;

import java.util.List;

public interface AIProvider {
    String name();

    ChatResult chat(ChatRequest request);

    List<Float> embed(String text);

    record ChatMessage(String role, String content) {}

    record ChatRequest(String model, List<ChatMessage> messages, Double temperature) {}

    record ChatResult(String content, String model, Integer promptTokens, Integer completionTokens, long latencyMs) {}
}
