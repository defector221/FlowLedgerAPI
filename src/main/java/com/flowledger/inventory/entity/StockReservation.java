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
@Table(name = "stock_reservations")
@Getter
@Setter
@NoArgsConstructor
public class StockReservation extends AuditedEntity {
    public enum Status {
        ACTIVE,
        RELEASED,
        CONSUMED,
        EXPIRED
    }

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal qty;

    @Column(name = "reference_type", nullable = false, length = 60)
    private String referenceType;

    @Column(name = "reference_id", nullable = false)
    private UUID referenceId;

    @Column(name = "expires_at")
    private OffsetDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.ACTIVE;
}
