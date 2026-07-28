package com.flowledger.commerce.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.commerce.audit.entity.CommerceConfigAuditLog;
import com.flowledger.commerce.audit.repository.CommerceConfigAuditLogRepository;
import com.flowledger.common.tenant.TenantContext;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CommerceAuditService {
    private final CommerceConfigAuditLogRepository repository;
    private final ObjectMapper objectMapper;

    public CommerceAuditService(CommerceConfigAuditLogRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public void log(UUID organizationId, String entityType, UUID entityId, String operation, String before, String after) {
        CommerceConfigAuditLog log = new CommerceConfigAuditLog();
        log.setOrganizationId(organizationId);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setOperation(operation);
        log.setBeforeJson(normalizeJson(before));
        log.setAfterJson(normalizeJson(after));
        log.setCreatedBy(TenantContext.userId().orElse(null));
        repository.save(log);
    }

    /** PostgreSQL jsonb requires valid JSON; wrap plain text (e.g. job ids) as a JSON string. */
    private String normalizeJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[") || trimmed.startsWith("\"") || "null".equals(trimmed)) {
            return trimmed;
        }
        try {
            return objectMapper.writeValueAsString(trimmed);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }
}
