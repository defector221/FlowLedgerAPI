package com.flowledger.ai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ai_usage_budgets")
@Getter
@Setter
@NoArgsConstructor
public class AiUsageBudget {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false, unique = true)
    private UUID organizationId;

    @Column(name = "daily_token_limit", nullable = false)
    private int dailyTokenLimit = 200_000;

    @Column(name = "tokens_used_today", nullable = false)
    private int tokensUsedToday;

    @Column(name = "usage_date", nullable = false)
    private LocalDate usageDate = LocalDate.now();

    @Column(name = "hard_stop", nullable = false)
    private boolean hardStop = true;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }
}
