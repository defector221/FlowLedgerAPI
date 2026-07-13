package com.flowledger.marketing.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "marketing_sends")
@Getter
@Setter
@NoArgsConstructor
public class MarketingSend {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "enrollment_id", nullable = false)
    private UUID enrollmentId;

    @Column(name = "step_id", nullable = false)
    private UUID stepId;

    @Column(nullable = false)
    private String channel;

    private String recipient;
    private String subject;

    @Column(columnDefinition = "text")
    private String body;

    @Column(nullable = false)
    private String status = "PENDING";

    private OffsetDateTime scheduledAt;
    private OffsetDateTime sentAt;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void created() {
        createdAt = OffsetDateTime.now();
    }
}
