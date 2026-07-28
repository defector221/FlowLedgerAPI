package com.flowledger.commerce.fulfillment.pickup.repository;

import com.flowledger.commerce.fulfillment.pickup.entity.PickupSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PickupSessionRepository extends JpaRepository<PickupSession, UUID> {
    Optional<PickupSession> findByFulfillmentOrderId(UUID fulfillmentOrderId);

    List<PickupSession> findByStoreId(UUID storeId);
}
