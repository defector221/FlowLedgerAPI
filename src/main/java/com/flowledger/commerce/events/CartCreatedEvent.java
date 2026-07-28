package com.flowledger.commerce.events;

import java.util.UUID;

public class CartCreatedEvent extends CommerceDomainEvent {
    public CartCreatedEvent(Object source, UUID organizationId, UUID customerId, UUID cartId) {
        super(source, organizationId, customerId, "CommerceCart", cartId);
    }
}
