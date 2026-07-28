package com.flowledger.commerce.events;

import java.util.UUID;

public class CustomerActivatedEvent extends CommerceDomainEvent {
    public CustomerActivatedEvent(Object source, UUID customerId) {
        super(source, null, null, "CommerceCustomer", customerId);
    }
}
