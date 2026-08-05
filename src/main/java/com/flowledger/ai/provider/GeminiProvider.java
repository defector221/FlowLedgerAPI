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

/** Google Gemini generateContent provider. */
public class GeminiProvider implements AIProvider {
    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public GeminiProvider(AiProperties properties, ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
    }

    @Override
    public String name() {
        return "GEMINI";
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
                    "Gemini mock (no API key): " + preview,
                    "mock-gemini",
                    0,
                    0,
                    System.currentTimeMillis() - start);
        }
        try {
            StringBuilder prompt = new StringBuilder();
            for (ChatMessage m : request.messages()) {
                prompt.append(m.role()).append(": ").append(m.content()).append('\n');
            }
            Map<String, Object> body = new HashMap<>();
            body.put("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt.toString())))));
            String uri =
                    "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key="
                            + apiKey;
            String raw = restClient
                    .post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = objectMapper.readTree(raw);
            String content = root.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text")
                    .asText("");
            return new ChatResult(content, "gemini-1.5-flash", null, null, System.currentTimeMillis() - start);
        } catch (Exception e) {
            throw new AiProviderException("Gemini chat failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Float> embed(String text) {
        List<Float> out = new ArrayList<>(32);
        int hash = text == null ? 0 : text.hashCode();
        for (int i = 0; i < 32; i++) {
            out.add(((hash * 31 + i) & 0xff) / 255f);
        }
        return out;
    }
}
