package com.flowledger.commerce.events;

import java.util.UUID;

public class ReadyForPickupEvent extends CommerceDomainEvent {
    public ReadyForPickupEvent(Object source, UUID organizationId, UUID actorId, UUID fulfillmentOrderId) {
        super(source, organizationId, actorId, "FulfillmentOrder", fulfillmentOrderId);
    }
}
