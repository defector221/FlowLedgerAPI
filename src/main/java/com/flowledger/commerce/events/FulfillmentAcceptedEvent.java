package com.flowledger.commerce.events;

import java.util.UUID;

public class FulfillmentAcceptedEvent extends CommerceDomainEvent {
    public FulfillmentAcceptedEvent(Object source, UUID organizationId, UUID actorId, UUID fulfillmentOrderId) {
        super(source, organizationId, actorId, "FulfillmentOrder", fulfillmentOrderId);
    }
}
