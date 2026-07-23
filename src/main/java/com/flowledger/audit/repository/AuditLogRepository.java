package com.flowledger.audit.repository;

import com.flowledger.audit.entity.AuditLog;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {
    Optional<AuditLog> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
