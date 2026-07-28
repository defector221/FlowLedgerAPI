package com.flowledger.commerce.events;

import java.util.UUID;

public class DeliveredEvent extends CommerceDomainEvent {
    public DeliveredEvent(Object source, UUID organizationId, UUID actorId, UUID fulfillmentOrderId) {
        super(source, organizationId, actorId, "FulfillmentOrder", fulfillmentOrderId);
    }
}
