package com.flowledger.commerce.events;

import java.util.UUID;

public class CommerceOrderPlacedEvent extends CommerceDomainEvent {
    public CommerceOrderPlacedEvent(Object source, UUID organizationId, UUID customerId, UUID orderId) {
        super(source, organizationId, customerId, "CommerceOrder", orderId);
    }
}
