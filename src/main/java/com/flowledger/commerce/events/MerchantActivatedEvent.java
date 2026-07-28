package com.flowledger.commerce.events;

import java.util.UUID;

public class MerchantActivatedEvent extends CommerceDomainEvent {
    public MerchantActivatedEvent(Object source, UUID organizationId, UUID actorId) {
        super(source, organizationId, actorId, "MerchantOnboarding", organizationId);
    }
}
