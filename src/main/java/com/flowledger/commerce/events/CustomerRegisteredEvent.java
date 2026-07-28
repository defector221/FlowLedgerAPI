package com.flowledger.commerce.events;

import java.util.UUID;

public class CustomerRegisteredEvent extends CommerceDomainEvent {
    public CustomerRegisteredEvent(Object source, UUID customerId) {
        super(source, null, null, "CommerceCustomer", customerId);
    }
}
