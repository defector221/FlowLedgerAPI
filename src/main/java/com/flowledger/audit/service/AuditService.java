package com.flowledger.audit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowledger.audit.entity.AuditLog;
import com.flowledger.audit.repository.AuditLogRepository;
import com.flowledger.common.tenant.TenantContext;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void log(String action, String entityType, UUID entityId, JsonNode oldValue, JsonNode newValue) {
        AuditLog log = new AuditLog();
        log.setOrganizationId(TenantContext.getOrganizationId());
        log.setUserId(TenantContext.userId().orElse(null));
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setOldValue(oldValue);
        log.setNewValue(newValue);
        repository.save(log);
    }

    public Page<AuditLog> list(Pageable pageable) {
        return repository.findByOrganizationIdOrderByCreatedAtDesc(TenantContext.getOrganizationId(), pageable);
    }
}
