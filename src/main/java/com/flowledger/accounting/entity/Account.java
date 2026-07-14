package com.flowledger.accounting.entity;

import com.flowledger.accounting.domain.AccountSubType;
import com.flowledger.accounting.domain.AccountType;
import com.flowledger.accounting.domain.SystemAccountKey;
import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
public class Account extends AuditedEntity {
    @Column(name = "account_code", nullable = false)
    private String accountCode;

    @Column(name = "account_name", nullable = false)
    private String accountName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_sub_type")
    private AccountSubType accountSubType;

    @Column(name = "parent_account_id")
    private UUID parentAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "system_account_key")
    private SystemAccountKey systemAccountKey;

    @Column(name = "system_account", nullable = false)
    private boolean systemAccount;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "allow_manual_posting", nullable = false)
    private boolean allowManualPosting = true;

    @Column(name = "opening_debit", nullable = false, precision = 19, scale = 4)
    private BigDecimal openingDebit = BigDecimal.ZERO;

    @Column(name = "opening_credit", nullable = false, precision = 19, scale = 4)
    private BigDecimal openingCredit = BigDecimal.ZERO;
}
