package com.flowledger.commerce.events;

import java.util.UUID;

public class PartnerCatalogSyncedEvent extends CommerceDomainEvent {
    public PartnerCatalogSyncedEvent(Object source, UUID organizationId, UUID actorId) {
        super(source, organizationId, actorId, "MerchantIntegrationProfile", organizationId);
    }
}
