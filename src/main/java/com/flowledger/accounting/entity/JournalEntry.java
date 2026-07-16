package com.flowledger.accounting.entity;

import com.flowledger.accounting.domain.JournalSource;
import com.flowledger.accounting.domain.JournalStatus;
import com.flowledger.accounting.domain.VoucherType;
import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "journal_entries")
@Getter
@Setter
@NoArgsConstructor
public class JournalEntry extends AuditedEntity {
    @Column(name = "fiscal_year_id", nullable = false)
    private UUID fiscalYearId;

    @Column(name = "accounting_period_id", nullable = false)
    private UUID accountingPeriodId;

    @Column(name = "entry_number", nullable = false)
    private String entryNumber;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "posting_date")
    private LocalDate postingDate;

    @Column(name = "reference_type")
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "voucher_type", nullable = false)
    private VoucherType voucherType = VoucherType.JOURNAL;

    @Column(name = "voucher_number")
    private String voucherNumber;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JournalStatus status = JournalStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JournalSource source = JournalSource.MANUAL;

    @Column(name = "reversal_of_id")
    private UUID reversalOfId;

    @Column(name = "reversed_by_id")
    private UUID reversedById;

    @Column(name = "total_debit", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalDebit = BigDecimal.ZERO;

    @Column(name = "total_credit", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalCredit = BigDecimal.ZERO;

    @Column(name = "posted_at")
    private OffsetDateTime postedAt;

    @Column(name = "posted_by")
    private UUID postedBy;
}
