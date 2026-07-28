package com.flowledger.commerce.fulfillment.delivery.entity;

import com.flowledger.commerce.common.CommerceGlobalEntity;
import com.flowledger.commerce.fulfillment.delivery.domain.DeliveryAssignmentStatus;
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
@Table(name = "commerce_delivery_assignments")
@Getter
@Setter
@NoArgsConstructor
public class DeliveryAssignment extends CommerceGlobalEntity {
    @Column(name = "fulfillment_order_id", nullable = false, unique = true)
    private UUID fulfillmentOrderId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "driver_id")
    private UUID driverId;

    @Column(name = "vehicle_id")
    private UUID vehicleId;

    @Column(name = "shipment_id")
    private UUID shipmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryAssignmentStatus status = DeliveryAssignmentStatus.PENDING;

    @Column(name = "assigned_at")
    private OffsetDateTime assignedAt;

    @Column(name = "dispatched_at")
    private OffsetDateTime dispatchedAt;

    @Column(name = "delivered_at")
    private OffsetDateTime deliveredAt;
}
