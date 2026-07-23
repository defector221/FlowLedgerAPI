package com.flowledger.retail.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "retail_loyalty_tiers")
@Getter
@Setter
@NoArgsConstructor
public class RetailLoyaltyTier extends RetailAuditedEntity {
    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(name = "min_points", nullable = false, precision = 18, scale = 2)
    private BigDecimal minPoints = BigDecimal.ZERO;

    @Column(name = "earn_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal earnRate = BigDecimal.ONE;
}
