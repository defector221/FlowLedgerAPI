package com.flowledger.sales.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.flowledger.tax.service.TaxLineCalculator;
import jakarta.persistence.*;
import java.math.*;
import java.util.*;
import lombok.*;

@Entity
@Table(name = "sales_invoice_items")
@Getter
@Setter
@NoArgsConstructor
public class SalesInvoiceItem implements TaxLineCalculator.LineTaxSnapshots {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_invoice_id")
    private SalesInvoice salesInvoice;

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

    private int lineOrder;

    @Column(name = "inventory_batch_id")
    private UUID inventoryBatchId;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Column(name = "allocation_mode", length = 20)
    private String allocationMode;

    @Column(name = "stock_reservation_id")
    private UUID stockReservationId;

    @Column(name = "tax_category_id")
    private UUID taxCategoryId;

    @Column(name = "tax_rule_id")
    private UUID taxRuleId;

    @Column(name = "tax_category_code", length = 50)
    private String taxCategoryCode;

    @Column(name = "tax_rule_version", length = 50)
    private String taxRuleVersion;
}
