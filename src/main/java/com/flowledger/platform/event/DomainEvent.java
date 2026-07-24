package com.flowledger.platform.event;

import java.time.Instant;
import java.util.UUID;
import org.springframework.context.ApplicationEvent;

/**
 * Base envelope for ERP domain events. All modules should publish via {@link DomainEventPublisher}.
 */
public abstract class DomainEvent extends ApplicationEvent {
    private final UUID organizationId;
    private final UUID actorId;
    private final UUID correlationId;
    private final Instant occurredAt;
    private final String entityType;
    private final UUID entityId;

    protected DomainEvent(
            Object source, UUID organizationId, UUID actorId, UUID correlationId, String entityType, UUID entityId) {
        super(source);
        this.organizationId = organizationId;
        this.actorId = actorId;
        this.correlationId = correlationId != null ? correlationId : UUID.randomUUID();
        this.occurredAt = Instant.now();
        this.entityType = entityType;
        this.entityId = entityId;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public String getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }
}
