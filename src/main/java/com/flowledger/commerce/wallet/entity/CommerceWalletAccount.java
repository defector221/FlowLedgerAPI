package com.flowledger.commerce.wallet.entity;

import com.flowledger.commerce.common.CommerceGlobalEntity;
import com.flowledger.commerce.wallet.domain.WalletAccountType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "commerce_wallet_accounts")
@Getter
@Setter
@NoArgsConstructor
public class CommerceWalletAccount extends CommerceGlobalEntity {
    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private WalletAccountType accountType;

    @Column(nullable = false)
    private String currency = "INR";

    @Column(nullable = false)
    private BigDecimal balance = BigDecimal.ZERO;
}
