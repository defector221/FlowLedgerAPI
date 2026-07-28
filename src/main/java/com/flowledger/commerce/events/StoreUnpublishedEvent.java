package com.flowledger.commerce.events;

import java.util.UUID;

public class StoreUnpublishedEvent extends CommerceDomainEvent {
    public StoreUnpublishedEvent(Object source, UUID organizationId, UUID actorId, UUID storeId) {
        super(source, organizationId, actorId, "StoreCommerceProfile", storeId);
    }
}
