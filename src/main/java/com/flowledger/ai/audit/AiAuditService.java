package com.flowledger.ai.audit;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.entity.AiAuditLog;
import com.flowledger.ai.repository.AiAuditLogRepository;
import com.flowledger.common.tenant.TenantContext;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnAiEnabled
public class AiAuditService {
    private final AiAuditLogRepository repository;

    public AiAuditService(AiAuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(
            String action,
            String requestSummary,
            String responseSummary,
            String model,
            Integer tokens,
            Integer latencyMs,
            String error) {
        AiAuditLog log = new AiAuditLog();
        log.setOrganizationId(TenantContext.getOrganizationId());
        log.setUserId(TenantContext.userId().orElse(null));
        log.setAction(action);
        log.setRequestSummary(truncate(requestSummary, 2000));
        log.setResponseSummary(truncate(responseSummary, 2000));
        log.setModel(model);
        log.setTokens(tokens);
        log.setLatencyMs(latencyMs);
        log.setError(truncate(error, 1000));
        repository.save(log);
    }

    @Transactional
    public void record(UUID organizationId, UUID userId, String action, String requestSummary, String responseSummary) {
        AiAuditLog log = new AiAuditLog();
        log.setOrganizationId(organizationId);
        log.setUserId(userId);
        log.setAction(action);
        log.setRequestSummary(truncate(requestSummary, 2000));
        log.setResponseSummary(truncate(responseSummary, 2000));
        repository.save(log);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
