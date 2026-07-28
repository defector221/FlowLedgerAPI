package com.flowledger.commerce.fulfillment.notification;

import com.flowledger.commerce.fulfillment.order.entity.FulfillmentOrder;
import com.flowledger.platform.event.DomainEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class FulfillmentNotificationHooks {
    private final DomainEventPublisher events;

    public FulfillmentNotificationHooks(DomainEventPublisher events) {
        this.events = events;
    }

    public void publish(com.flowledger.platform.event.DomainEvent event) {
        events.publish(event);
    }
}
