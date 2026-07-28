package com.flowledger.commerce.events;

import java.util.UUID;

public class StoreCommerceEnabledEvent extends CommerceDomainEvent {
    public StoreCommerceEnabledEvent(Object source, UUID organizationId, UUID actorId, UUID storeId) {
        super(source, organizationId, actorId, "StoreCommerceProfile", storeId);
    }
}
