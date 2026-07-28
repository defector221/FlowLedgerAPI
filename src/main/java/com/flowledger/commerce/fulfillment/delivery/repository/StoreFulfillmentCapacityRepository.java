package com.flowledger.commerce.fulfillment.delivery.repository;

import com.flowledger.commerce.fulfillment.delivery.entity.StoreFulfillmentCapacity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreFulfillmentCapacityRepository extends JpaRepository<StoreFulfillmentCapacity, UUID> {
    Optional<StoreFulfillmentCapacity> findByStoreId(UUID storeId);
}
