package com.flowledger.commerce.events;

import java.util.UUID;

public class PackingCompletedEvent extends CommerceDomainEvent {
    public PackingCompletedEvent(Object source, UUID organizationId, UUID actorId, UUID fulfillmentOrderId) {
        super(source, organizationId, actorId, "FulfillmentOrder", fulfillmentOrderId);
    }
}
