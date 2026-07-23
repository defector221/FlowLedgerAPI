package com.flowledger.retail.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "retail_loyalty_accounts")
@Getter
@Setter
@NoArgsConstructor
public class RetailLoyaltyAccount extends AuditedEntity {
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "tier_id")
    private UUID tierId;

    @Column(name = "points_balance", nullable = false, precision = 18, scale = 2)
    private BigDecimal pointsBalance = BigDecimal.ZERO;

    @Column(name = "lifetime_points", nullable = false, precision = 18, scale = 2)
    private BigDecimal lifetimePoints = BigDecimal.ZERO;
}
