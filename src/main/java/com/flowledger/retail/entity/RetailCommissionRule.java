package com.flowledger.retail.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "retail_commission_rules")
@Getter
@Setter
@NoArgsConstructor
public class RetailCommissionRule extends RetailAuditedEntity {
    @Column(nullable = false)
    private String name;

    @Column(name = "rate_percent", nullable = false, precision = 8, scale = 4)
    private BigDecimal ratePercent = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;
}
