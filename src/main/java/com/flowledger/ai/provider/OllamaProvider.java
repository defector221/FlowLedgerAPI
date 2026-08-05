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

/** Local Ollama OpenAI-compatible chat/embeddings provider. */
public class OllamaProvider implements AIProvider {
    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OllamaProvider(AiProperties properties, ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public String name() {
        return "OLLAMA";
    }

    @Override
    public ChatResult chat(ChatRequest request) {
        long start = System.currentTimeMillis();
        String base = ollamaBase();
        String model = request.model() == null || request.model().isBlank()
                ? properties.getOpenai().getChatModel()
                : request.model();
        try {
            List<Map<String, String>> messages = new ArrayList<>();
            for (ChatMessage m : request.messages()) {
                messages.add(Map.of("role", m.role(), "content", m.content()));
            }
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            body.put("stream", false);
            String raw = restClient
                    .post()
                    .uri(base + "/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(raw);
            String content = root.path("message").path("content").asText("");
            return new ChatResult(content, model, null, null, System.currentTimeMillis() - start);
        } catch (Exception e) {
            throw new AiProviderException("Ollama chat failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Float> embed(String text) {
        String base = ollamaBase();
        try {
            Map<String, Object> body = Map.of(
                    "model",
                    properties.getOpenai().getEmbeddingModel(),
                    "prompt",
                    text == null ? "" : text);
            String raw = restClient
                    .post()
                    .uri(base + "/api/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(raw);
            List<Float> out = new ArrayList<>();
            for (JsonNode n : root.path("embedding")) {
                out.add((float) n.asDouble());
            }
            if (out.isEmpty()) {
                return mockEmbed(text);
            }
            return out;
        } catch (Exception e) {
            return mockEmbed(text);
        }
    }

    private String ollamaBase() {
        String configured = properties.getOpenai().getBaseUrl();
        if (configured != null && configured.contains("11434")) {
            return trimSlash(configured.replace("/v1", ""));
        }
        return "http://localhost:11434";
    }

    private static List<Float> mockEmbed(String text) {
        List<Float> out = new ArrayList<>(32);
        int hash = text == null ? 0 : text.hashCode();
        for (int i = 0; i < 32; i++) {
            out.add(((hash >> (i % 24)) & 0xff) / 255f);
        }
        return out;
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
