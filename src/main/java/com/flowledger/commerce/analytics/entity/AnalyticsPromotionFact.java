package com.flowledger.commerce.analytics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "analytics_promotion_facts")
@Getter
@Setter
@NoArgsConstructor
public class AnalyticsPromotionFact {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "rule_id")
    private UUID ruleId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "discount_applied", nullable = false)
    private BigDecimal discountApplied;

    @Column(name = "outcome_type")
    private String outcomeType;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    @PrePersist
    void onCreate() {
        if (occurredAt == null) occurredAt = OffsetDateTime.now();
    }
}
