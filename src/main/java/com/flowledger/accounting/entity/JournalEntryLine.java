package com.flowledger.accounting.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "journal_entry_lines")
@Getter
@Setter
@NoArgsConstructor
public class JournalEntryLine extends AuditedEntity {
    @Column(name = "journal_entry_id", nullable = false)
    private UUID journalEntryId;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    private String description;

    @Column(name = "debit_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal debitAmount = BigDecimal.ZERO;

    @Column(name = "credit_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal creditAmount = BigDecimal.ZERO;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "cost_center_id")
    private UUID costCenterId;

    private String reference;
}
