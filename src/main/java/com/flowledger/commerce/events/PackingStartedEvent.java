package com.flowledger.commerce.events;

import java.util.UUID;

public class PackingStartedEvent extends CommerceDomainEvent {
    public PackingStartedEvent(Object source, UUID organizationId, UUID actorId, UUID fulfillmentOrderId) {
        super(source, organizationId, actorId, "FulfillmentOrder", fulfillmentOrderId);
    }
}
