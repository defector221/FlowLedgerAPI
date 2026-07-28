package com.flowledger.tax.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "tax_rule_audit_log")
@Getter
@Setter
@NoArgsConstructor
public class TaxRuleAuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "tax_category_id", nullable = false)
    private UUID taxCategoryId;

    @Column(name = "previous_rule_id")
    private UUID previousRuleId;

    @Column(name = "new_rule_id", nullable = false)
    private UUID newRuleId;

    @Column(nullable = false, length = 30)
    private String operation;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void created() {
        createdAt = OffsetDateTime.now();
    }
}
