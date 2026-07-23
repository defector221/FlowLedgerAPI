package com.flowledger.retail.entity;

import com.flowledger.retail.domain.RetailEnums.GiftCardStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "retail_gift_cards")
@Getter
@Setter
@NoArgsConstructor
public class RetailGiftCard extends RetailAuditedEntity {
    @Column(name = "card_number", nullable = false)
    private String cardNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GiftCardStatus status = GiftCardStatus.ISSUED;

    @Column(name = "initial_balance", nullable = false, precision = 18, scale = 2)
    private BigDecimal initialBalance;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal balance;

    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @Column(name = "activated_at")
    private OffsetDateTime activatedAt;
}
