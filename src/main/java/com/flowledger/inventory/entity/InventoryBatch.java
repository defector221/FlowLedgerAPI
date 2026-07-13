package com.flowledger.inventory.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;

@Entity
@Table(
        name = "inventory_batches",
        uniqueConstraints =
                @UniqueConstraint(columnNames = {"organization_id", "product_id", "warehouse_id", "batch_number"}))
@Getter
@Setter
@NoArgsConstructor
public class InventoryBatch extends AuditedEntity {
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "batch_number", nullable = false)
    private String batchNumber;

    private LocalDate expiryDate;

    @Column(nullable = false)
    private BigDecimal quantity = BigDecimal.ZERO;
}
