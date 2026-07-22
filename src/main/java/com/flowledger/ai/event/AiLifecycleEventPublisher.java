package com.flowledger.ai.event;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publishes {@link AiLifecycleEvent} for AI side-effects. Prefer calling from AFTER_COMMIT bridges
 * rather than from ERP posting methods (zero ERP edits preferred).
 */
@Component
@ConditionalOnAiEnabled
public class AiLifecycleEventPublisher {
    private final ApplicationEventPublisher events;

    public AiLifecycleEventPublisher(ApplicationEventPublisher events) {
        this.events = events;
    }

    public void publish(AiLifecycleEvent event) {
        events.publishEvent(event);
    }

    public void recommendationSeed(UUID organizationId, String entityType, UUID entityId) {
        publish(AiLifecycleEvent.recommendationSeed(organizationId, entityType, entityId));
    }

    public void knowledgeRefresh(UUID organizationId, String entityType, UUID entityId) {
        publish(AiLifecycleEvent.knowledgeRefresh(organizationId, entityType, entityId));
    }

    public void publish(
            UUID organizationId, String eventType, String entityType, UUID entityId, Map<String, Object> payload) {
        publish(new AiLifecycleEvent(organizationId, eventType, entityType, entityId, payload));
    }
}
