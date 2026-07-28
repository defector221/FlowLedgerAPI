package com.flowledger.commerce.events;

import java.util.UUID;

public class MerchantRegisteredEvent extends CommerceDomainEvent {
    public MerchantRegisteredEvent(Object source, UUID organizationId, UUID actorId) {
        super(source, organizationId, actorId, "MerchantOnboarding", organizationId);
    }
}
