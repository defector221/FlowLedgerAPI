package com.flowledger.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.ai.config.AiProperties;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * OpenAI chat/embeddings via RestClient. LangChain4j OpenAiChatModel is available on the classpath
 * for future AiServices tool binding; chat path stays RestClient for mock/no-key behavior.
 */
public class OpenAIProvider implements AIProvider {
    private static final Logger log = LoggerFactory.getLogger(OpenAIProvider.class);

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public OpenAIProvider(AiProperties properties, ObjectMapper objectMapper, RestClient aiRestClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = aiRestClient;
    }

    @Override
    public String name() {
        return "OPENAI";
    }

    @Override
    public ChatResult chat(ChatRequest request) {
        long start = System.currentTimeMillis();
        String model = request.model() != null && !request.model().isBlank()
                ? request.model()
                : properties.getOpenai().getChatModel();

        if (!properties.hasApiKey()) {
            String preview = request.messages().isEmpty()
                    ? ""
                    : request.messages().get(request.messages().size() - 1).content();
            String mock = "AI mock response (no API key configured): " + truncate(preview, 240);
            return new ChatResult(mock, "mock-" + model, 0, mock.length() / 4, System.currentTimeMillis() - start);
        }

        try {
            List<Map<String, String>> messages = new ArrayList<>();
            for (ChatMessage m : request.messages()) {
                messages.add(Map.of("role", m.role(), "content", m.content()));
            }
            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("messages", messages);
            if (request.temperature() != null) {
                body.put("temperature", request.temperature());
            }

            String raw = restClient
                    .post()
                    .uri(trimSlash(properties.getOpenai().getBaseUrl()) + "/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getOpenai().getApiKey())
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(raw);
            String content =
                    root.path("choices").path(0).path("message").path("content").asText("");
            Integer promptTokens = root.path("usage").path("prompt_tokens").isMissingNode()
                    ? null
                    : root.path("usage").path("prompt_tokens").asInt();
            Integer completionTokens =
                    root.path("usage").path("completion_tokens").isMissingNode()
                            ? null
                            : root.path("usage").path("completion_tokens").asInt();
            return new ChatResult(content, model, promptTokens, completionTokens, System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("OpenAI chat failed: {}", e.getMessage());
            throw new AiProviderException("AI temporarily unavailable", e);
        }
    }

    @Override
    public List<Float> embed(String text) {
        if (!properties.hasApiKey()) {
            return mockEmbedding(text);
        }
        try {
            Map<String, Object> body =
                    Map.of("model", properties.getOpenai().getEmbeddingModel(), "input", text == null ? "" : text);
            String raw = restClient
                    .post()
                    .uri(trimSlash(properties.getOpenai().getBaseUrl()) + "/embeddings")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + properties.getOpenai().getApiKey())
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode arr = objectMapper.readTree(raw).path("data").path(0).path("embedding");
            List<Float> values = new ArrayList<>();
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    values.add((float) n.asDouble());
                }
            }
            return values;
        } catch (Exception e) {
            log.warn("OpenAI embedding failed, using mock: {}", e.getMessage());
            return mockEmbedding(text);
        }
    }

    private static List<Float> mockEmbedding(String text) {
        int dim = 32;
        List<Float> out = new ArrayList<>(dim);
        int seed = text == null ? 0 : text.hashCode();
        for (int i = 0; i < dim; i++) {
            out.add((float) Math.sin(seed * 0.001 + i));
        }
        return out;
    }

    private static String trimSlash(String url) {
        if (url == null || url.isBlank()) {
            return "https://api.openai.com/v1";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
