package com.flowledger.platform.approval.entity;

import com.flowledger.platform.approval.domain.ApprovalActionType;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "approval_instance_actions")
@Getter
@Setter
@NoArgsConstructor
public class ApprovalInstanceAction {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "instance_id", nullable = false)
    private UUID instanceId;

    @Column(name = "level_number", nullable = false)
    private int levelNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalActionType action;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "acted_at", nullable = false)
    private OffsetDateTime actedAt = OffsetDateTime.now();

    @Column(columnDefinition = "text")
    private String remarks;
}
