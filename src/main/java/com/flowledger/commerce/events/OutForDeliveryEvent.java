package com.flowledger.commerce.events;

import java.util.UUID;

public class OutForDeliveryEvent extends CommerceDomainEvent {
    public OutForDeliveryEvent(Object source, UUID organizationId, UUID actorId, UUID fulfillmentOrderId) {
        super(source, organizationId, actorId, "FulfillmentOrder", fulfillmentOrderId);
    }
}
