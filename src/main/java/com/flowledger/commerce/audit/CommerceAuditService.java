package com.flowledger.commerce.audit;

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

    public CommerceAuditService(CommerceConfigAuditLogRepository repository) {
        this.repository = repository;
    }

    public void log(UUID organizationId, String entityType, UUID entityId, String operation, String before, String after) {
        CommerceConfigAuditLog log = new CommerceConfigAuditLog();
        log.setOrganizationId(organizationId);
        log.setEntityType(entityType);
        log.setEntityId(entityId);
        log.setOperation(operation);
        log.setBeforeJson(before);
        log.setAfterJson(after);
        log.setCreatedBy(TenantContext.userId().orElse(null));
        repository.save(log);
    }
}
