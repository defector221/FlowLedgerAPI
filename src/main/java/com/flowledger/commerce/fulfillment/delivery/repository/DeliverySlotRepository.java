package com.flowledger.commerce.fulfillment.delivery.repository;

import com.flowledger.commerce.fulfillment.delivery.entity.DeliverySlot;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliverySlotRepository extends JpaRepository<DeliverySlot, UUID> {
    List<DeliverySlot> findByStoreIdAndSlotDateAndActiveTrue(UUID storeId, LocalDate slotDate);
}
