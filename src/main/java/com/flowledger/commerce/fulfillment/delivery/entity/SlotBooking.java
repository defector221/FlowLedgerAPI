package com.flowledger.commerce.fulfillment.delivery.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "commerce_slot_bookings")
@Getter
@Setter
@NoArgsConstructor
public class SlotBooking {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "commerce_order_id")
    private UUID commerceOrderId;

    @Column(name = "fulfillment_order_id")
    private UUID fulfillmentOrderId;

    @Column(name = "slot_type", nullable = false)
    private String slotType;

    @Column(name = "delivery_slot_id")
    private UUID deliverySlotId;

    @Column(name = "pickup_slot_id")
    private UUID pickupSlotId;

    @Column(name = "booked_at", nullable = false)
    private OffsetDateTime bookedAt = OffsetDateTime.now();

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = OffsetDateTime.now();
    }
}
