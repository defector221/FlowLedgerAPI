package com.flowledger.sales.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.*;
import java.util.*;
import lombok.*;

@Entity
@Table(name = "quotation_items")
@Getter
@Setter
@NoArgsConstructor
public class QuotationItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quotation_id")
    private Quotation quotation;

    @Column(nullable = false)
    private UUID productId;

    private String description, hsnSacCode;
    @Column(nullable = false)
    private BigDecimal quantity, rate;
    private UUID unitId;
    @Column(nullable = false)
    private BigDecimal discountPercent = BigDecimal.ZERO,
            discountAmount = BigDecimal.ZERO,
            taxRate = BigDecimal.ZERO,
            taxableAmount = BigDecimal.ZERO,
            cgstAmount = BigDecimal.ZERO,
            sgstAmount = BigDecimal.ZERO,
            igstAmount = BigDecimal.ZERO,
            lineTotal = BigDecimal.ZERO;

    @Column(name = "tax_type", nullable = false, length = 16)
    private String taxType = "GST";

    @Column(name = "split_strategy", nullable = false, length = 32)
    private String splitStrategy = "PLACE_OF_SUPPLY";

    @Column(name = "cgst_share_percent", nullable = false, precision = 7, scale = 4)
    private BigDecimal cgstSharePercent = new BigDecimal("50");

    @Column(name = "sgst_share_percent", nullable = false, precision = 7, scale = 4)
    private BigDecimal sgstSharePercent = new BigDecimal("50");

    private int lineOrder;
}
