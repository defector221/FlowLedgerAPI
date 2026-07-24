package com.flowledger.platform.approval.dto;

import com.flowledger.platform.approval.domain.ApprovalStatus;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ApprovalDtos {
    private ApprovalDtos() {}

    public record SubmitRequest(String entityType, UUID entityId, BigDecimal amount, String remarks) {}

    public record DecideRequest(String remarks) {}

    public record DefinitionRequest(
            String entityType,
            String name,
            String levelsJson,
            BigDecimal minAmount,
            BigDecimal maxAmount,
            Boolean active) {}

    public record DefinitionResponse(
            UUID id,
            String entityType,
            String name,
            boolean active,
            String levelsJson,
            BigDecimal minAmount,
            BigDecimal maxAmount) {}

    public record ActionResponse(
            UUID id, int levelNumber, String action, UUID actorId, OffsetDateTime actedAt, String remarks) {}

    public record InstanceResponse(
            UUID id,
            String entityType,
            UUID entityId,
            ApprovalStatus status,
            int currentLevel,
            int totalLevels,
            UUID requestedBy,
            OffsetDateTime requestedAt,
            UUID decidedBy,
            OffsetDateTime decidedAt,
            BigDecimal amount,
            String remarks,
            List<ActionResponse> actions) {}
}
