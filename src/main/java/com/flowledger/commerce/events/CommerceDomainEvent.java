package com.flowledger.commerce.events;

import com.flowledger.platform.event.DomainEvent;
import java.util.UUID;

public abstract class CommerceDomainEvent extends DomainEvent {
    protected CommerceDomainEvent(Object source, UUID organizationId, UUID actorId, String entityType, UUID entityId) {
        super(source, organizationId, actorId, null, entityType, entityId);
    }
}
