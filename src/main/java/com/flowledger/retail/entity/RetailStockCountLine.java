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
@Table(name = "retail_stock_count_lines")
@Getter
@Setter
@NoArgsConstructor
public class RetailStockCountLine extends AuditedEntity {
    @Column(name = "count_id", nullable = false)
    private UUID countId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "system_qty", nullable = false, precision = 18, scale = 4)
    private BigDecimal systemQty = BigDecimal.ZERO;

    @Column(name = "counted_qty", nullable = false, precision = 18, scale = 4)
    private BigDecimal countedQty = BigDecimal.ZERO;

    @Column(name = "variance_qty", nullable = false, precision = 18, scale = 4)
    private BigDecimal varianceQty = BigDecimal.ZERO;
}
