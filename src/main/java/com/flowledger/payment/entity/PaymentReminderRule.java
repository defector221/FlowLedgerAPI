package com.flowledger.payment.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payment_reminder_rules")
@Getter
@Setter
@NoArgsConstructor
public class PaymentReminderRule {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(nullable = false)
    private String name;

    @Column(name = "days_offset", nullable = false)
    private int daysOffset;

    @Column(name = "offset_type", nullable = false)
    private String offsetType = "AFTER_DUE";

    @Column(nullable = false)
    private String channel = "EMAIL";

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "subject_template")
    private String subjectTemplate;

    @Column(name = "body_template")
    private String bodyTemplate;

    @Column(name = "created_at")
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "updated_by")
    private UUID updatedBy;

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
