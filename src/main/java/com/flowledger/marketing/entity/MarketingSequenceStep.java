package com.flowledger.marketing.entity;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "marketing_sequence_steps")
@Getter
@Setter
@NoArgsConstructor
public class MarketingSequenceStep {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sequence_id", nullable = false)
    private MarketingSequence sequence;

    @Column(name = "step_order", nullable = false)
    private int stepOrder;

    @Column(name = "delay_days", nullable = false)
    private int delayDays = 0;

    @Column(nullable = false)
    private String channel = "EMAIL";

    @Column(name = "subject_template")
    private String subjectTemplate;

    @Column(name = "body_template", columnDefinition = "text")
    private String bodyTemplate = "";

    @Column(name = "email_template_id")
    private UUID emailTemplateId;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void created() {
        createdAt = OffsetDateTime.now();
    }
}
