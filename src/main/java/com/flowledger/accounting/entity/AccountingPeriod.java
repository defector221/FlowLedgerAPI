package com.flowledger.accounting.entity;

import com.flowledger.accounting.domain.PeriodStatus;
import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "accounting_periods")
@Getter
@Setter
@NoArgsConstructor
public class AccountingPeriod extends AuditedEntity {
    @Column(name = "fiscal_year_id", nullable = false)
    private UUID fiscalYearId;

    @Column(name = "period_number", nullable = false)
    private int periodNumber;

    @Column(nullable = false)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PeriodStatus status = PeriodStatus.OPEN;
}
