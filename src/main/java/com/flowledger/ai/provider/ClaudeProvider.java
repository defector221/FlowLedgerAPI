package com.flowledger.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.ai.config.AiProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/** Anthropic Messages API provider (Claude). */
public class ClaudeProvider implements AIProvider {
    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public ClaudeProvider(AiProperties properties, ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public String name() {
        return "CLAUDE";
    }

    @Override
    public ChatResult chat(ChatRequest request) {
        long start = System.currentTimeMillis();
        String apiKey = properties.getOpenai().getApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            String preview = request.messages().isEmpty()
                    ? ""
                    : request.messages().get(request.messages().size() - 1).content();
            return new ChatResult(
                    "Claude mock (no API key): " + preview,
                    "mock-claude",
                    0,
                    0,
                    System.currentTimeMillis() - start);
        }
        try {
            String system = "";
            List<Map<String, String>> messages = new ArrayList<>();
            for (ChatMessage m : request.messages()) {
                if ("system".equalsIgnoreCase(m.role())) {
                    system = m.content();
                } else {
                    messages.add(Map.of("role", m.role(), "content", m.content()));
                }
            }
            Map<String, Object> body = new HashMap<>();
            body.put("model", "claude-3-5-haiku-latest");
            body.put("max_tokens", 1024);
            body.put("messages", messages);
            if (!system.isBlank()) {
                body.put("system", system);
            }
            String raw = restClient
                    .post()
                    .uri("https://api.anthropic.com/v1/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(raw);
            String content = root.path("content").path(0).path("text").asText("");
            return new ChatResult(content, root.path("model").asText("claude"), null, null, System.currentTimeMillis() - start);
        } catch (Exception e) {
            throw new AiProviderException("Claude chat failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Float> embed(String text) {
        // Claude has no native embeddings; fall back to deterministic local vector.
        List<Float> out = new ArrayList<>(32);
        int hash = text == null ? 0 : text.hashCode();
        for (int i = 0; i < 32; i++) {
            out.add(((hash >> (i % 24)) & 0xff) / 255f);
        }
        return out;
    }
}
