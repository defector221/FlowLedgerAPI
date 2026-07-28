package com.flowledger.ops.audit;

import com.flowledger.ops.entity.PlatformAuditLog;
import com.flowledger.ops.repository.PlatformAuditLogRepository;
import com.flowledger.ops.security.PlatformPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class OpsAuditService {
    private final PlatformAuditLogRepository auditLogs;

    public OpsAuditService(PlatformAuditLogRepository auditLogs) {
        this.auditLogs = auditLogs;
    }

    @Transactional
    public void record(String action, UUID organizationId, String targetType, String targetId, String status) {
        PlatformAuditLog log = new PlatformAuditLog();
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof PlatformPrincipal p) {
            log.setActorUserId(p.getId());
            log.setActorEmail(p.getEmail());
            log.setActorRole(p.getRoles().stream().findFirst().orElse(null));
        }
        log.setAction(action);
        log.setOrganizationId(organizationId);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setStatus(status == null ? "SUCCESS" : status);
        HttpServletRequest req = currentRequest();
        if (req != null) {
            log.setIpAddress(req.getRemoteAddr());
            String ua = req.getHeader("User-Agent");
            if (ua != null && ua.length() > 500) {
                ua = ua.substring(0, 500);
            }
            log.setUserAgent(ua);
            log.setCorrelationId(req.getHeader("X-Correlation-Id"));
        }
        auditLogs.save(log);
    }

    @Transactional(readOnly = true)
    public Page<PlatformAuditLog> list(Pageable pageable) {
        return auditLogs.findAllByOrderByCreatedAtDesc(pageable);
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest();
        }
        return null;
    }
}
