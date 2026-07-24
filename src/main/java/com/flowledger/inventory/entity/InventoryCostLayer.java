package com.flowledger.inventory.entity;

import com.flowledger.common.entity.AuditedEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "inventory_cost_layers")
@Getter
@Setter
@NoArgsConstructor
public class InventoryCostLayer extends AuditedEntity {
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "qty_remaining", nullable = false, precision = 18, scale = 4)
    private BigDecimal qtyRemaining = BigDecimal.ZERO;

    @Column(name = "unit_cost", nullable = false, precision = 19, scale = 6)
    private BigDecimal unitCost = BigDecimal.ZERO;

    @Column(name = "received_at", nullable = false)
    private OffsetDateTime receivedAt = OffsetDateTime.now();

    @Column(nullable = false, length = 10)
    private String method = "WAC";
}
