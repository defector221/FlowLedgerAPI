package com.flowledger.ops.repository;

import com.flowledger.ops.entity.PlatformAuditLog;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAuditLogRepository extends JpaRepository<PlatformAuditLog, UUID> {
    Page<PlatformAuditLog> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
