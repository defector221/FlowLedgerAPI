package com.flowledger.commerce.reservation.entity;

import com.flowledger.commerce.common.CommerceGlobalEntity;
import com.flowledger.commerce.reservation.domain.CommerceReservationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "commerce_inventory_reservations")
@Getter
@Setter
@NoArgsConstructor
public class CommerceInventoryReservation extends CommerceGlobalEntity {
    @Column(name = "cart_id", nullable = false)
    private UUID cartId;

    @Column(name = "cart_item_id", nullable = false)
    private UUID cartItemId;

    @Column(name = "stock_reservation_id")
    private UUID stockReservationId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Column(name = "warehouse_id", nullable = false)
    private UUID warehouseId;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommerceReservationStatus status = CommerceReservationStatus.ACTIVE;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "renewed_at")
    private OffsetDateTime renewedAt;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "scan_session_id")
    private UUID scanSessionId;
}
