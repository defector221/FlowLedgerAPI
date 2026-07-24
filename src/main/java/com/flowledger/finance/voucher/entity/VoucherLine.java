package com.flowledger.finance.voucher.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "voucher_lines")
@Getter
@Setter
@NoArgsConstructor
public class VoucherLine extends AuditedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voucher_id", nullable = false)
    private Voucher voucher;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal debit = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal credit = BigDecimal.ZERO;

    private String description;

    @Column(name = "cost_center_id")
    private UUID costCenterId;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "project_id")
    private UUID projectId;

    @Column(name = "warehouse_id")
    private UUID warehouseId;

    @Column(name = "inventory_reference")
    private String inventoryReference;

    @Column(name = "tax_rate_id")
    private UUID taxRateId;

    @Column(name = "tax_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;
}
