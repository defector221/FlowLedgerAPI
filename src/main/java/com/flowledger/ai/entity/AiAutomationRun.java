package com.flowledger.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ai_automation_runs")
@Getter
@Setter
@NoArgsConstructor
public class AiAutomationRun {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "automation_id", nullable = false)
    private UUID automationId;

    @Column(name = "trigger_source", nullable = false)
    private String triggerSource;

    @Column(nullable = false)
    private String status;

    @Column(name = "dry_run", nullable = false)
    private boolean dryRun;

    @Column(name = "signal_count", nullable = false)
    private int signalCount;

    @Column(name = "action_count", nullable = false)
    private int actionCount;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "started_at", updatable = false)
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    @PrePersist
    void onCreate() {
        startedAt = OffsetDateTime.now();
    }
}
