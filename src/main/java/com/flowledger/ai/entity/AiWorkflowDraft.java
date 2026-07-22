package com.flowledger.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "ai_workflow_drafts")
@Getter
@Setter
public class AiWorkflowDraft {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(nullable = false)
    private String name;

    @Column(name = "trigger_type", nullable = false, length = 64)
    private String triggerType = "MANUAL";

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "conditions_json", columnDefinition = "TEXT")
    private String conditionsJson;

    @Column(name = "steps_json", columnDefinition = "TEXT")
    private String stepsJson;

    @Column(name = "suggested_approvers", columnDefinition = "TEXT")
    private String suggestedApprovers;

    @Column(nullable = false, length = 32)
    private String status = "DRAFT";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
