package com.flowledger.platform.event;

import com.flowledger.common.tenant.TenantContext;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class DomainEventPublisher {
    private final ApplicationEventPublisher publisher;

    public DomainEventPublisher(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    public void publish(DomainEvent event) {
        publisher.publishEvent(event);
    }

    public UUID currentOrg() {
        return TenantContext.organizationId().orElse(null);
    }

    public UUID currentUser() {
        return TenantContext.userId().orElse(null);
    }
}
