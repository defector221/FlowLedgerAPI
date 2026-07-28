package com.flowledger.commerce.events;

import java.util.UUID;

public class MerchantCapabilityChangedEvent extends CommerceDomainEvent {
    public MerchantCapabilityChangedEvent(Object source, UUID organizationId, UUID actorId, UUID profileId) {
        super(source, organizationId, actorId, "MerchantCapabilityProfile", profileId);
    }
}
