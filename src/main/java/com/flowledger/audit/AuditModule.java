package com.flowledger.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.flowledger.common.tenant.TenantContext;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(nullable = false)
    private String action;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id")
    private UUID entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "old_value", columnDefinition = "jsonb")
    private JsonNode oldValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "new_value", columnDefinition = "jsonb")
    private JsonNode newValue;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    void created() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }
}

interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    Page<AuditLog> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);
}

@Service
class AuditService {
    private final AuditLogRepository repository;

    AuditService(AuditLogRepository repository) {
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

    Page<AuditLog> list(Pageable pageable) {
        return repository.findByOrganizationIdOrderByCreatedAtDesc(TenantContext.getOrganizationId(), pageable);
    }
}

@RestController
@RequestMapping("/api/v1/audit-logs")
class AuditController {
    private final AuditService service;

    AuditController(AuditService service) {
        this.service = service;
    }

    @GetMapping
    public Page<AuditLog> list(Pageable pageable) {
        return service.list(pageable);
    }
}
