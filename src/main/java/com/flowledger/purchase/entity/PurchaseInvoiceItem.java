package com.flowledger.purchase.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "purchase_invoice_items")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseInvoiceItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "purchase_invoice_id")
    private PurchaseInvoice invoice;

    private UUID productId, unitId;
    private String description, hsnSacCode;
    private BigDecimal quantity,
            rate,
            discountPercent = BigDecimal.ZERO,
            discountAmount = BigDecimal.ZERO,
            taxRate = BigDecimal.ZERO,
            taxableAmount = BigDecimal.ZERO,
            cgstRate = BigDecimal.ZERO,
            sgstRate = BigDecimal.ZERO,
            igstRate = BigDecimal.ZERO,
            cgstAmount = BigDecimal.ZERO,
            sgstAmount = BigDecimal.ZERO,
            igstAmount = BigDecimal.ZERO,
            cessAmount = BigDecimal.ZERO,
            lineTotal = BigDecimal.ZERO;

    @Column(name = "tax_type", nullable = false, length = 16)
    private String taxType = "GST";

    @Column(name = "split_strategy", nullable = false, length = 32)
    private String splitStrategy = "PLACE_OF_SUPPLY";

    @Column(name = "cgst_share_percent", nullable = false, precision = 7, scale = 4)
    private BigDecimal cgstSharePercent = new BigDecimal("50");

    @Column(name = "sgst_share_percent", nullable = false, precision = 7, scale = 4)
    private BigDecimal sgstSharePercent = new BigDecimal("50");

    private Integer lineOrder = 0;
}
