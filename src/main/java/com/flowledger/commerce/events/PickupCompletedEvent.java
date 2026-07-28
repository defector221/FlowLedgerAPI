package com.flowledger.commerce.events;

import java.util.UUID;

public class PickupCompletedEvent extends CommerceDomainEvent {
    public PickupCompletedEvent(Object source, UUID organizationId, UUID actorId, UUID fulfillmentOrderId) {
        super(source, organizationId, actorId, "FulfillmentOrder", fulfillmentOrderId);
    }
}
