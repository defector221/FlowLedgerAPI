package com.flowledger.migration.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.migration.entity.MigrationAuditLog;
import com.flowledger.migration.repository.MigrationAuditLogRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class MigrationAuditService {
    private final MigrationAuditLogRepository repo;
    private final ObjectMapper objectMapper;

    public MigrationAuditService(MigrationAuditLogRepository repo, ObjectMapper objectMapper) {
        this.repo = repo;
        this.objectMapper = objectMapper;
    }

    public void log(UUID jobId, String action, String module, Map<String, Object> detail) {
        MigrationAuditLog log = new MigrationAuditLog();
        log.setOrganizationId(TenantContext.getOrganizationId());
        log.setJobId(jobId);
        log.setAction(action);
        log.setModule(module);
        log.setActorId(TenantContext.userId().orElse(null));
        try {
            log.setDetailJson(objectMapper.writeValueAsString(detail == null ? Map.of() : detail));
        } catch (Exception e) {
            log.setDetailJson("{}");
        }
        repo.save(log);
    }
}
