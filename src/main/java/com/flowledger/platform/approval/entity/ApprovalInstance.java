package com.flowledger.platform.approval.entity;

import com.flowledger.common.entity.AuditedEntity;
import com.flowledger.platform.approval.domain.ApprovalStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "approval_instances")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalInstance extends AuditedEntity {
    @Column(name = "definition_id")
    private UUID definitionId;

    @Column(name = "entity_type", nullable = false)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(name = "current_level", nullable = false)
    private int currentLevel = 1;

    @Column(name = "total_levels", nullable = false)
    private int totalLevels = 1;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt = OffsetDateTime.now();

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(columnDefinition = "text")
    private String remarks;
}
