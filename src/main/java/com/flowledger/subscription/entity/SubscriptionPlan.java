package com.flowledger.subscription.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "subscription_plans")
@Getter
@Setter
@NoArgsConstructor
public class SubscriptionPlan {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "max_organizations", nullable = false)
    private int maxOrganizations = 1;

    @Column(name = "max_users_per_org", nullable = false)
    private int maxUsersPerOrg = 3;

    @Column(name = "max_invoices_per_month", nullable = false)
    private int maxInvoicesPerMonth = 50;

    @Column(name = "price_monthly", nullable = false)
    private BigDecimal priceMonthly = BigDecimal.ZERO;

    @Column(name = "price_yearly", nullable = false)
    private BigDecimal priceYearly = BigDecimal.ZERO;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "display_order", nullable = false)
    private int displayOrder = 0;

    @Column(name = "highlight_plan", nullable = false)
    private boolean highlightPlan = false;

    @Column(nullable = false)
    private boolean recommended = false;

    @Column(name = "trial_days", nullable = false)
    private int trialDays = 0;

    private boolean active = true;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    void created() {
        var now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void updated() {
        updatedAt = OffsetDateTime.now();
    }
}
