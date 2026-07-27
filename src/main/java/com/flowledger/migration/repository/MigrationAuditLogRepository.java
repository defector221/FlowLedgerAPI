package com.flowledger.migration.repository;

import com.flowledger.migration.entity.MigrationAuditLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MigrationAuditLogRepository extends JpaRepository<MigrationAuditLog, UUID> {}
