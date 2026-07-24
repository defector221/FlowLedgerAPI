package com.flowledger.platform.history.service;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.platform.history.entity.DocumentHistoryEntry;
import com.flowledger.platform.history.repository.DocumentHistoryRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentHistoryService {
    private final DocumentHistoryRepository repository;

    public DocumentHistoryService(DocumentHistoryRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public DocumentHistoryEntry record(
            String entityType, UUID entityId, String eventType, String summary, String detailJson) {
        DocumentHistoryEntry entry = new DocumentHistoryEntry();
        entry.setOrganizationId(TenantContext.getOrganizationId());
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setEventType(eventType);
        entry.setSummary(summary);
        entry.setDetailJson(detailJson);
        entry.setActorId(TenantContext.userId().orElse(null));
        entry.setOccurredAt(OffsetDateTime.now());
        entry.setCorrelationId(UUID.randomUUID());
        return repository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<DocumentHistoryEntry> timeline(String entityType, UUID entityId) {
        return repository.findByOrganizationIdAndEntityTypeAndEntityIdOrderByOccurredAtDesc(
                TenantContext.getOrganizationId(), entityType, entityId);
    }
}
