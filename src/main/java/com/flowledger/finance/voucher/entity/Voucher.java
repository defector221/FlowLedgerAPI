package com.flowledger.finance.voucher.entity;

import com.flowledger.common.entity.AuditedEntity;
import com.flowledger.finance.voucher.domain.VoucherStatus;
import com.flowledger.finance.voucher.domain.VoucherType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "vouchers")
@Getter
@Setter
@NoArgsConstructor
public class Voucher extends AuditedEntity {
    @Column(name = "branch_id")
    private UUID branchId;

    @Column(name = "voucher_number", nullable = false)
    private String voucherNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "voucher_type", nullable = false)
    private VoucherType voucherType;

    @Column(name = "voucher_date", nullable = false)
    private LocalDate voucherDate;

    @Column(name = "currency_code", nullable = false)
    private String currencyCode = "INR";

    @Column(name = "exchange_rate", nullable = false, precision = 19, scale = 8)
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Column(name = "reference_type")
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(columnDefinition = "text")
    private String narration;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VoucherStatus status = VoucherStatus.DRAFT;

    @Column(name = "total_debit", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalDebit = BigDecimal.ZERO;

    @Column(name = "total_credit", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalCredit = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean posted = false;

    @Column(name = "posted_at")
    private OffsetDateTime postedAt;

    @Column(name = "posted_by")
    private UUID postedBy;

    @Column(name = "journal_entry_id")
    private UUID journalEntryId;

    @Column(name = "reversed_voucher_id")
    private UUID reversedVoucherId;

    @Column(name = "reversal_of_id")
    private UUID reversalOfId;

    @Column(name = "is_recurring", nullable = false)
    private boolean recurring = false;

    @Column(name = "recurring_template_id")
    private UUID recurringTemplateId;

    @Column(name = "recurrence_rule", columnDefinition = "text")
    private String recurrenceRule;

    @Version
    private Long version;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @OneToMany(mappedBy = "voucher", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private List<VoucherLine> lines = new ArrayList<>();
}
