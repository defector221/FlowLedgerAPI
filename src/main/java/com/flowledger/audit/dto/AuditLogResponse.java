package com.flowledger.audit.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        UUID organizationId,
        UUID userId,
        String userName,
        String userEmail,
        String action,
        String entityType,
        UUID entityId,
        JsonNode oldValue,
        JsonNode newValue,
        String ipAddress,
        String userAgent,
        OffsetDateTime createdAt) {}
