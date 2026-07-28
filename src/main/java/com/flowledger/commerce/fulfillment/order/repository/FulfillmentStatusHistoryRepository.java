package com.flowledger.commerce.fulfillment.order.repository;

import com.flowledger.commerce.fulfillment.order.entity.FulfillmentStatusHistory;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FulfillmentStatusHistoryRepository extends JpaRepository<FulfillmentStatusHistory, UUID> {
    List<FulfillmentStatusHistory> findByFulfillmentOrderIdOrderByCreatedAtAsc(UUID fulfillmentOrderId);
}
