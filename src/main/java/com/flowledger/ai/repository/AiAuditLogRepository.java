package com.flowledger.ai.repository;

import com.flowledger.ai.entity.AiAuditLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiAuditLogRepository extends JpaRepository<AiAuditLog, UUID> {}
