package com.flowledger.commerce.events;

import java.util.UUID;

public class DeliveryAssignedEvent extends CommerceDomainEvent {
    public DeliveryAssignedEvent(Object source, UUID organizationId, UUID actorId, UUID fulfillmentOrderId) {
        super(source, organizationId, actorId, "FulfillmentOrder", fulfillmentOrderId);
    }
}
