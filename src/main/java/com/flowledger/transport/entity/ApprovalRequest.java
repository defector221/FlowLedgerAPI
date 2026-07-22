package com.flowledger.transport.entity;

import com.flowledger.transport.domain.TransportEnums.ApprovalStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "approval_requests")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalRequest extends TransportAuditedEntity {
    @Column(nullable = false)
    private String entityType;

    @Column(nullable = false)
    private UUID entityId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalStatus status = ApprovalStatus.PENDING;

    @Column(nullable = false)
    private UUID requestedBy;

    @Column(nullable = false)
    private OffsetDateTime requestedAt;

    private UUID decidedBy;
    private OffsetDateTime decidedAt;

    @Column(columnDefinition = "text")
    private String remarks;

    @Column(name = "workflow_draft_id")
    private UUID workflowDraftId;

    @Column(name = "workflow_name")
    private String workflowName;

    @Column(name = "current_step", nullable = false)
    private int currentStep = 1;

    @Column(name = "total_steps", nullable = false)
    private int totalSteps = 1;

    @Column(name = "steps_snapshot_json", columnDefinition = "text")
    private String stepsSnapshotJson;
}
