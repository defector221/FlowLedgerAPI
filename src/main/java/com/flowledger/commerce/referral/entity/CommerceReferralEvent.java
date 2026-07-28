package com.flowledger.commerce.referral.entity;

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
@Table(name = "commerce_referral_events")
@Getter
@Setter
@NoArgsConstructor
public class CommerceReferralEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "referrer_customer_id", nullable = false)
    private UUID referrerCustomerId;

    @Column(name = "referee_customer_id", nullable = false)
    private UUID refereeCustomerId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(name = "rewarded_at")
    private OffsetDateTime rewardedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
