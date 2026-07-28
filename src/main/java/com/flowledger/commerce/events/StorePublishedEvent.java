package com.flowledger.commerce.events;

import java.util.UUID;

public class StorePublishedEvent extends CommerceDomainEvent {
    public StorePublishedEvent(Object source, UUID organizationId, UUID actorId, UUID storeId) {
        super(source, organizationId, actorId, "StoreCommerceProfile", storeId);
    }
}
