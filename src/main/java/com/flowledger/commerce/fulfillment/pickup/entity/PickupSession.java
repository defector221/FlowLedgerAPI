package com.flowledger.commerce.fulfillment.pickup.entity;

import com.flowledger.commerce.common.CommerceGlobalEntity;
import com.flowledger.commerce.fulfillment.pickup.domain.PickupSessionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "commerce_pickup_sessions")
@Getter
@Setter
@NoArgsConstructor
public class PickupSession extends CommerceGlobalEntity {
    @Column(name = "fulfillment_order_id", nullable = false, unique = true)
    private UUID fulfillmentOrderId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PickupSessionStatus status = PickupSessionStatus.WAITING;

    @Column(name = "arrived_at")
    private OffsetDateTime arrivedAt;

    @Column(name = "collected_at")
    private OffsetDateTime collectedAt;
}
