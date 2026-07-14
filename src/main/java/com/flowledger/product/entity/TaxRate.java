package com.flowledger.product.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import java.math.*;
import lombok.*;

@Entity
@Table(name = "tax_rates")
@Getter
@Setter
@NoArgsConstructor
public class TaxRate extends AuditedEntity {
    @Column(nullable = false)
    private String name;

    @Column(name = "tax_type", nullable = false, length = 16)
    @Enumerated(EnumType.STRING)
    private TaxType taxType = TaxType.GST;

    @Column(name = "split_strategy", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private SplitStrategy splitStrategy = SplitStrategy.PLACE_OF_SUPPLY;

    /** Share of total tax allocated to CGST (must sum with sgstSharePercent to 100 for split strategies). */
    @Column(name = "cgst_share_percent", nullable = false, precision = 7, scale = 4)
    private BigDecimal cgstSharePercent = new BigDecimal("50");

    @Column(name = "sgst_share_percent", nullable = false, precision = 7, scale = 4)
    private BigDecimal sgstSharePercent = new BigDecimal("50");

    @Column(nullable = false)
    private BigDecimal rate;

    @Column(name = "cgst_rate", nullable = false)
    private BigDecimal cgstRate = BigDecimal.ZERO;

    @Column(name = "sgst_rate", nullable = false)
    private BigDecimal sgstRate = BigDecimal.ZERO;

    @Column(name = "igst_rate", nullable = false)
    private BigDecimal igstRate = BigDecimal.ZERO;

    @Column(name = "cess_rate", nullable = false)
    private BigDecimal cessRate = BigDecimal.ZERO;

    @Column(nullable = false)
    private boolean active = true;
}
