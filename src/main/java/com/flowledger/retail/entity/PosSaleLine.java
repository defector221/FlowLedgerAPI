package com.flowledger.retail.entity;

import com.flowledger.common.entity.AuditedEntity;
import com.flowledger.tax.service.TaxLineCalculator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "pos_sale_lines")
@Getter
@Setter
@NoArgsConstructor
public class PosSaleLine extends AuditedEntity implements TaxLineCalculator.PosLineTaxSnapshots {
    @Column(name = "pos_sale_id", nullable = false)
    private UUID posSaleId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "variant_id")
    private UUID variantId;

    @Column(length = 500)
    private String description;

    private String barcode;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal quantity;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal rate;

    @Column(name = "discount_percent", nullable = false, precision = 8, scale = 4)
    private BigDecimal discountPercent = BigDecimal.ZERO;

    @Column(name = "tax_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal taxRate = BigDecimal.ZERO;

    @Column(name = "line_total", nullable = false, precision = 18, scale = 2)
    private BigDecimal lineTotal = BigDecimal.ZERO;

    @Column(name = "line_order", nullable = false)
    private int lineOrder;

    @Column(name = "inventory_batch_id")
    private UUID inventoryBatchId;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Column(name = "allocation_mode", length = 20)
    private String allocationMode;

    @Column(name = "tax_category_id")
    private UUID taxCategoryId;

    @Column(name = "tax_rule_id")
    private UUID taxRuleId;

    @Column(name = "tax_category_code", length = 50)
    private String taxCategoryCode;

    @Column(name = "tax_rule_version", length = 50)
    private String taxRuleVersion;
}
