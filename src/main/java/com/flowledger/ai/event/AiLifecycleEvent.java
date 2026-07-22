package com.flowledger.ai.event;

import java.util.Map;
import java.util.UUID;

/** Advisory AI lifecycle signal — published AFTER_COMMIT only; never inside ERP posting TX. */
public record AiLifecycleEvent(
        UUID organizationId, String eventType, String entityType, UUID entityId, Map<String, Object> payload) {

    public static final String RECOMMENDATION_SEED = "RECOMMENDATION_SEED";
    public static final String KNOWLEDGE_REFRESH = "KNOWLEDGE_REFRESH";
    public static final String FORECAST_REQUEST = "FORECAST_REQUEST";

    public static AiLifecycleEvent recommendationSeed(UUID orgId, String entityType, UUID entityId) {
        return new AiLifecycleEvent(orgId, RECOMMENDATION_SEED, entityType, entityId, Map.of());
    }

    public static AiLifecycleEvent knowledgeRefresh(UUID orgId, String entityType, UUID entityId) {
        return new AiLifecycleEvent(orgId, KNOWLEDGE_REFRESH, entityType, entityId, Map.of());
    }
}
