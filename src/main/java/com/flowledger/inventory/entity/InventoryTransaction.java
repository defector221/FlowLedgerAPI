package com.flowledger.inventory.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;

@Entity
@Table(name = "inventory_transactions")
@Getter
@Setter
@NoArgsConstructor
public class InventoryTransaction extends AuditedEntity {
    public enum Type {
        OPENING_STOCK,
        PURCHASE,
        PURCHASE_RETURN,
        SALE,
        SALES_RETURN,
        STOCK_ADJUSTMENT,
        STOCK_TRANSFER,
        DAMAGED,
        EXPIRED
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false)
    private Type transactionType;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "transaction_date", nullable = false)
    private LocalDate transactionDate;

    private String referenceType;
    private UUID referenceId;
    private String referenceNumber;

    @Column(nullable = false)
    private BigDecimal inwardQty = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal outwardQty = BigDecimal.ZERO;

    private BigDecimal unitCost;
    private String batchNumber;
    private String serialNumber;
    private LocalDate expiryDate;

    @Column(columnDefinition = "text")
    private String notes;

    private String idempotencyKey;
}
