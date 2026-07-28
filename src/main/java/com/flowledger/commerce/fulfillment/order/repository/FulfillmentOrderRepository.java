package com.flowledger.commerce.fulfillment.order.repository;

import com.flowledger.commerce.fulfillment.order.domain.FulfillmentOrderStatus;
import com.flowledger.commerce.fulfillment.order.entity.FulfillmentOrder;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FulfillmentOrderRepository extends JpaRepository<FulfillmentOrder, UUID> {
    Optional<FulfillmentOrder> findByCommerceOrderId(UUID commerceOrderId);

    List<FulfillmentOrder> findByStoreIdAndStatusIn(UUID storeId, List<FulfillmentOrderStatus> statuses);

    List<FulfillmentOrder> findByOrganizationIdAndStatusIn(UUID organizationId, List<FulfillmentOrderStatus> statuses);

    List<FulfillmentOrder> findByStoreIdOrderByCreatedAtDesc(UUID storeId);

    List<FulfillmentOrder> findByStoreIdAndStatusOrderByCompletedAtDesc(
            UUID storeId, FulfillmentOrderStatus status);

    long countByStoreIdAndStatus(UUID storeId, FulfillmentOrderStatus status);
}
