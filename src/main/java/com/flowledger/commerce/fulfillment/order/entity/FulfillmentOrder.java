package com.flowledger.commerce.fulfillment.order.entity;

import com.flowledger.commerce.common.CommerceGlobalEntity;
import com.flowledger.commerce.fulfillment.FulfillmentType;
import com.flowledger.commerce.fulfillment.order.domain.FulfillmentOrderStatus;
import com.flowledger.commerce.fulfillment.order.domain.FulfillmentSubStatus;
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
@Table(name = "commerce_fulfillment_orders")
@Getter
@Setter
@NoArgsConstructor
public class FulfillmentOrder extends CommerceGlobalEntity {
    @Column(name = "commerce_order_id", nullable = false, unique = true, updatable = false)
    private UUID commerceOrderId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "store_id", nullable = false, updatable = false)
    private UUID storeId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_type", nullable = false, updatable = false)
    private FulfillmentType fulfillmentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FulfillmentOrderStatus status = FulfillmentOrderStatus.CREATED;

    @Enumerated(EnumType.STRING)
    @Column(name = "sub_status")
    private FulfillmentSubStatus subStatus = FulfillmentSubStatus.NONE;

    @Column(name = "assigned_picker_id")
    private UUID assignedPickerId;

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    @Column(name = "ready_at")
    private OffsetDateTime readyAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;
}
