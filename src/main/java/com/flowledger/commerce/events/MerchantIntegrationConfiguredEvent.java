package com.flowledger.commerce.events;

import java.util.UUID;

public class MerchantIntegrationConfiguredEvent extends CommerceDomainEvent {
    public MerchantIntegrationConfiguredEvent(Object source, UUID organizationId, UUID actorId, UUID profileId) {
        super(source, organizationId, actorId, "MerchantIntegrationProfile", profileId);
    }
}
