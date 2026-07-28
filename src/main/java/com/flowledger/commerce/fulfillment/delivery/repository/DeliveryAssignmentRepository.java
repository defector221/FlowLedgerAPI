package com.flowledger.commerce.fulfillment.delivery.repository;

import com.flowledger.commerce.fulfillment.delivery.domain.DeliveryAssignmentStatus;
import com.flowledger.commerce.fulfillment.delivery.entity.DeliveryAssignment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryAssignmentRepository extends JpaRepository<DeliveryAssignment, UUID> {
    Optional<DeliveryAssignment> findByFulfillmentOrderId(UUID fulfillmentOrderId);

    List<DeliveryAssignment> findByStoreIdAndStatusIn(UUID storeId, List<DeliveryAssignmentStatus> statuses);
}
