package com.flowledger.accounting.entity;

import com.flowledger.accounting.domain.FiscalYearStatus;
import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "fiscal_years")
@Getter
@Setter
@NoArgsConstructor
public class FiscalYear extends AuditedEntity {
    @Column(nullable = false)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FiscalYearStatus status = FiscalYearStatus.OPEN;

    @Column(name = "is_current", nullable = false)
    private boolean current;
}
