package com.flowledger.commerce.events;

import java.util.UUID;

public class FulfillmentOrderCreatedEvent extends CommerceDomainEvent {
    public FulfillmentOrderCreatedEvent(Object source, UUID organizationId, UUID actorId, UUID fulfillmentOrderId) {
        super(source, organizationId, actorId, "FulfillmentOrder", fulfillmentOrderId);
    }
}
