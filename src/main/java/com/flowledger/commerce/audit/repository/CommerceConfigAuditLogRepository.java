package com.flowledger.commerce.audit.repository;

import com.flowledger.commerce.audit.entity.CommerceConfigAuditLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommerceConfigAuditLogRepository extends JpaRepository<CommerceConfigAuditLog, UUID> {}
