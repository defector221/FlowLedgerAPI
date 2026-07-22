package com.flowledger.transport.service;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.transport.entity.IntegrationOutbox;
import com.flowledger.transport.repository.IntegrationOutboxRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class IntegrationOutboxService {
    private final IntegrationOutboxRepository repository;
    public IntegrationOutboxService(IntegrationOutboxRepository repository) { this.repository = repository; }

    public IntegrationOutbox enqueue(String eventType, String aggregateType, UUID aggregateId, String payloadJson) {
        IntegrationOutbox outbox = new IntegrationOutbox();
        outbox.setOrganizationId(TenantContext.getOrganizationId());
        outbox.setEventType(eventType);
        outbox.setAggregateType(aggregateType);
        outbox.setAggregateId(aggregateId);
        outbox.setPayloadJson(payloadJson == null ? "{}" : payloadJson);
        return repository.save(outbox);
    }
}
