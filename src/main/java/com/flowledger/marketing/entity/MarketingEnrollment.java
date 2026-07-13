package com.flowledger.marketing.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "marketing_enrollments")
@Getter
@Setter
@NoArgsConstructor
public class MarketingEnrollment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "sequence_id", nullable = false)
    private UUID sequenceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sequence_id", insertable = false, updatable = false)
    private MarketingSequence sequence;

    @Column(name = "recipient_type", nullable = false)
    private String recipientType;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    private String email;
    private String phone;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Column(name = "current_step", nullable = false)
    private int currentStep = 0;

    @Column(name = "enrolled_at", nullable = false)
    private OffsetDateTime enrolledAt;

    private OffsetDateTime completedAt;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    void created() {
        var now = OffsetDateTime.now();
        if (enrolledAt == null) {
            enrolledAt = now;
        }
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updated() {
        updatedAt = OffsetDateTime.now();
    }
}
