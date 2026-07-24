package com.flowledger.accounting.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "ledger_balances")
@Getter
@Setter
@NoArgsConstructor
public class LedgerBalance {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "fiscal_year_id", nullable = false)
    private UUID fiscalYearId;

    @Column(name = "accounting_period_id")
    private UUID accountingPeriodId;

    @Column(name = "debit_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal debitTotal = BigDecimal.ZERO;

    @Column(name = "credit_total", nullable = false, precision = 19, scale = 4)
    private BigDecimal creditTotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }
}
