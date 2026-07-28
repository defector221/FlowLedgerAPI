package com.flowledger.commerce.events;

import java.util.UUID;

public class PickingStartedEvent extends CommerceDomainEvent {
    public PickingStartedEvent(Object source, UUID organizationId, UUID actorId, UUID fulfillmentOrderId) {
        super(source, organizationId, actorId, "FulfillmentOrder", fulfillmentOrderId);
    }
}
