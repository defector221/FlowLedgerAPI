package com.flowledger.platform.event.bus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PlatformEventBus {
    private final PlatformEventOutboxRepository outbox;
    private final ObjectMapper objectMapper;

    public PlatformEventBus(PlatformEventOutboxRepository outbox, ObjectMapper objectMapper) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
    }

    public PlatformEventOutbox publish(
            String eventType,
            UUID organizationId,
            String aggregateType,
            UUID aggregateId,
            Map<String, Object> payload,
            UUID actorId,
            UUID correlationId) {
        PlatformEventOutbox row = new PlatformEventOutbox();
        row.setEventType(eventType);
        row.setEventVersion(1);
        row.setOrganizationId(organizationId);
        row.setAggregateType(aggregateType);
        row.setAggregateId(aggregateId);
        row.setActorId(actorId);
        row.setCorrelationId(correlationId != null ? correlationId : UUID.randomUUID());
        row.setPayload(toJson(payload));
        row.setOccurredAt(OffsetDateTime.now());
        return outbox.save(row);
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload != null ? payload : Map.of());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
