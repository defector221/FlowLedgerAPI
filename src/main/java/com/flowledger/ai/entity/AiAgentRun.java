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
import lombok.Setter;

@Entity
@Table(name = "ai_agent_runs")
@Getter
@Setter
public class AiAgentRun {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "conversation_id")
    private UUID conversationId;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "primary_agent", nullable = false, length = 64)
    private String primaryAgent;

    @Column(name = "consulted_agents", columnDefinition = "TEXT")
    private String consultedAgents;

    @Column(name = "message_preview", length = 500)
    private String messagePreview;

    @Column(name = "latency_ms")
    private Integer latencyMs;

    @Column(nullable = false, length = 32)
    private String status = "OK";

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
