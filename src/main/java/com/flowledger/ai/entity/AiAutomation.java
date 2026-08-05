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
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ai_automations")
@Getter
@Setter
@NoArgsConstructor
public class AiAutomation {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "trigger_type", nullable = false)
    private String triggerType = "CRON";

    @Column(name = "cron_expression")
    private String cronExpression;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "signal_type", nullable = false)
    private String signalType;

    @Column(name = "action_type", nullable = false)
    private String actionType;

    @Column(name = "action_config_json", nullable = false, columnDefinition = "text")
    private String actionConfigJson = "{}";

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun;

    @Column(nullable = false)
    private String status = "PAUSED";

    @Column(name = "last_run_at")
    private OffsetDateTime lastRunAt;

    @Column(name = "next_run_at")
    private OffsetDateTime nextRunAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
