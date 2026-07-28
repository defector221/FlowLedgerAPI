package com.flowledger.commerce.fulfillment.delivery.entity;

import com.flowledger.commerce.common.CommerceGlobalEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "commerce_pickup_slots")
@Getter
@Setter
@NoArgsConstructor
public class PickupSlot extends CommerceGlobalEntity {
    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "max_orders", nullable = false)
    private int maxOrders = 20;

    @Column(name = "booked_count", nullable = false)
    private int bookedCount;

    @Column(nullable = false)
    private boolean active = true;
}
