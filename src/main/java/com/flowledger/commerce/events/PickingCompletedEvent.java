package com.flowledger.commerce.events;

import java.util.UUID;

public class PickingCompletedEvent extends CommerceDomainEvent {
    public PickingCompletedEvent(Object source, UUID organizationId, UUID actorId, UUID fulfillmentOrderId) {
        super(source, organizationId, actorId, "FulfillmentOrder", fulfillmentOrderId);
    }
}
