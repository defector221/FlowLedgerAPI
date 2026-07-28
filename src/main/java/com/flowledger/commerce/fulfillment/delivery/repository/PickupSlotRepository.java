package com.flowledger.commerce.fulfillment.delivery.repository;

import com.flowledger.commerce.fulfillment.delivery.entity.PickupSlot;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PickupSlotRepository extends JpaRepository<PickupSlot, UUID> {
    List<PickupSlot> findByStoreIdAndSlotDateAndActiveTrue(UUID storeId, LocalDate slotDate);
}
