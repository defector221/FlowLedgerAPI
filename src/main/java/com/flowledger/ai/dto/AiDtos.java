package com.flowledger.ai.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AiDtos {
    private AiDtos() {}

    public record ChatRequest(UUID conversationId, String message, String agent, Boolean useRag) {}

    public record ChatResponse(
            UUID conversationId,
            UUID messageId,
            String agent,
            String content,
            String model,
            long latencyMs,
            List<String> consultedAgents) {
        public ChatResponse(
                UUID conversationId, UUID messageId, String agent, String content, String model, long latencyMs) {
            this(conversationId, messageId, agent, content, model, latencyMs, List.of());
        }
    }

    public record AgentInfo(
            String code,
            String displayName,
            String description,
            List<String> allowedTools,
            String permission,
            boolean supportsCollaboration) {}

    public record ConversationResponse(
            UUID id, String title, String agentType, String status, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}

    public record MessageResponse(
            UUID id,
            String role,
            String content,
            String model,
            Integer promptTokens,
            Integer completionTokens,
            Integer latencyMs,
            OffsetDateTime createdAt) {}

    public record HealthResponse(
            boolean enabled,
            String provider,
            boolean chatEnabled,
            boolean ragEnabled,
            boolean embeddingsEnabled,
            boolean analyticsEnabled,
            boolean documentAiEnabled,
            boolean voiceEnabled,
            boolean apiKeyConfigured,
            boolean multiAgentEnabled,
            boolean workflowBuilderEnabled) {
        public HealthResponse(
                boolean enabled,
                String provider,
                boolean chatEnabled,
                boolean ragEnabled,
                boolean embeddingsEnabled,
                boolean analyticsEnabled,
                boolean documentAiEnabled,
                boolean voiceEnabled,
                boolean apiKeyConfigured) {
            this(
                    enabled,
                    provider,
                    chatEnabled,
                    ragEnabled,
                    embeddingsEnabled,
                    analyticsEnabled,
                    documentAiEnabled,
                    voiceEnabled,
                    apiKeyConfigured,
                    true,
                    true);
        }
    }

    public record KnowledgeCreateRequest(String title, String docType, String content) {}

    public record KnowledgeResponse(
            UUID id, String title, String docType, String content, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}

    public record RecommendationResponse(
            UUID id,
            String type,
            String priority,
            String title,
            String description,
            BigDecimal confidence,
            String reason,
            Map<String, Object> evidence,
            String suggestedAction,
            String status,
            String relatedEntityType,
            UUID relatedEntityId,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record RecommendationPatchRequest(String status) {}

    public record RecommendationCreateRequest(
            String type,
            String priority,
            String title,
            String description,
            BigDecimal confidence,
            String reason,
            Map<String, Object> evidence,
            String suggestedAction,
            String relatedEntityType,
            UUID relatedEntityId) {}

    public record KnowledgeSearchResponse(List<KnowledgeResponse> documents) {}

    public record ForecastPoint(String period, BigDecimal actual, BigDecimal forecast) {}

    public record ForecastResponse(
            boolean enabled,
            String message,
            String type,
            UUID runId,
            List<ForecastPoint> points,
            Map<String, Object> summary) {}

    public record WorkflowSuggestRequest(String text) {}

    public record WorkflowSuggestResponse(boolean configured, String message, Map<String, Object> suggestedFields) {}

    public record DocumentAiRequest(String filename, String contentBase64) {}

    public record DocumentAiResponse(boolean configured, String message, Map<String, Object> result) {}

    public record VoiceAiRequest(String contentType, String audioBase64) {}

    public record VoiceAiResponse(
            boolean configured, String message, String transcript, Map<String, Object> result) {
        public VoiceAiResponse(boolean configured, String message, Map<String, Object> result) {
            this(configured, message, result == null ? null : (String) result.get("transcript"), result);
        }
    }

    public record VoiceChatRequest(UUID conversationId, String contentType, String audioBase64, String agent) {}

    public record WorkflowDraftRequest(
            String name,
            String triggerType,
            String description,
            String conditionsJson,
            String stepsJson,
            String suggestedApprovers) {}

    public record WorkflowDraftResponse(
            UUID id,
            String name,
            String triggerType,
            String description,
            String conditionsJson,
            String stepsJson,
            String suggestedApprovers,
            String status,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record WorkflowNlSuggestRequest(String prompt) {}
}
