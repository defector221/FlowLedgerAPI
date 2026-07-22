package com.flowledger.ai.workflow;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.ai.config.AiProperties;
import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.dto.AiDtos;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/** OpenAI Whisper transcription. Falls back to mock when voice disabled or no API key. */
@Service
@ConditionalOnAiEnabled
public class VoiceAiService {
    private static final Logger log = LoggerFactory.getLogger(VoiceAiService.class);

    private final AiProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public VoiceAiService(AiProperties properties, ObjectMapper objectMapper, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = restClientBuilder.build();
    }

    public AiDtos.VoiceAiResponse transcribe(AiDtos.VoiceAiRequest request) {
        if (!properties.isVoiceEnabled()) {
            return new AiDtos.VoiceAiResponse(
                    false, "Voice AI not configured. Set flowledger.ai.voice-enabled=true.", null, Map.of());
        }
        if (request == null
                || request.audioBase64() == null
                || request.audioBase64().isBlank()) {
            return new AiDtos.VoiceAiResponse(false, "audioBase64 is required", null, Map.of());
        }

        String contentType =
                request.contentType() == null || request.contentType().isBlank() ? "audio/webm" : request.contentType();

        if (!properties.hasApiKey()) {
            String mock = "Mock transcript (no API key): please configure OPENAI_API_KEY for Whisper.";
            Map<String, Object> result = new HashMap<>();
            result.put("transcript", mock);
            result.put("model", "mock-whisper");
            result.put("draftOnly", true);
            return new AiDtos.VoiceAiResponse(true, "Mock transcription — API key not configured.", mock, result);
        }

        try {
            byte[] audio = Base64.getDecoder().decode(request.audioBase64());
            MultipartBodyBuilder body = new MultipartBodyBuilder();
            body.part("file", new ByteArrayResource(audio) {
                @Override
                public String getFilename() {
                    return "audio" + extensionFor(contentType);
                }
            });
            body.part("model", properties.getOpenai().getWhisperModel());

            String raw = restClient
                    .post()
                    .uri(trimSlash(properties.getOpenai().getBaseUrl()) + "/audio/transcriptions")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .header("Authorization", "Bearer " + properties.getOpenai().getApiKey())
                    .body(body.build())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(raw);
            String transcript = root.path("text").asText("");
            Map<String, Object> result = new HashMap<>();
            result.put("transcript", transcript);
            result.put("model", properties.getOpenai().getWhisperModel());
            result.put("contentType", contentType);
            return new AiDtos.VoiceAiResponse(true, "Transcription complete.", transcript, result);
        } catch (IllegalArgumentException e) {
            return new AiDtos.VoiceAiResponse(false, "Invalid audioBase64", null, Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.warn("Whisper transcription failed: {}", e.getMessage());
            return new AiDtos.VoiceAiResponse(
                    false, "AI temporarily unavailable", null, Map.of("error", "transcription_failed"));
        }
    }

    private static String extensionFor(String contentType) {
        String ct = contentType.toLowerCase();
        if (ct.contains("wav")) {
            return ".wav";
        }
        if (ct.contains("mpeg") || ct.contains("mp3")) {
            return ".mp3";
        }
        if (ct.contains("ogg")) {
            return ".ogg";
        }
        if (ct.contains("mp4") || ct.contains("m4a")) {
            return ".m4a";
        }
        return ".webm";
    }

    private static String trimSlash(String base) {
        if (base == null || base.isBlank()) {
            return "https://api.openai.com/v1";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }
}
