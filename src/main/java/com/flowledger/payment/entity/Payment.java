package com.flowledger.payment.entity;

import com.flowledger.accounting.domain.AccountingStatus;
import com.flowledger.common.entity.AuditedEntity;
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
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
public class Payment extends AuditedEntity {
    public enum Type {
        RECEIPT,
        PAYMENT
    }

    public enum Party {
        CUSTOMER,
        SUPPLIER
    }

    @Column(name = "payment_number")
    private String paymentNumber;

    @Column(name = "payment_date")
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type")
    private Type paymentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "party_type")
    private Party partyType;

    private UUID customerId;
    private UUID supplierId;
    private BigDecimal amount;
    private String paymentMode;
    private String transactionReference;
    private String bankReference;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(nullable = false)
    private String status = "ACTIVE";

    @Enumerated(EnumType.STRING)
    @Column(name = "accounting_status", nullable = false)
    private AccountingStatus accountingStatus = AccountingStatus.NOT_POSTED;

    @Column(name = "posted_journal_entry_id")
    private UUID postedJournalEntryId;

    @Column(name = "accounting_posted_at")
    private OffsetDateTime accountingPostedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentAllocation> allocations = new ArrayList<>();
}
