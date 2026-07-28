package com.flowledger.commerce.order.repository;

import com.flowledger.commerce.order.entity.CommerceOrderLine;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommerceOrderLineRepository extends JpaRepository<CommerceOrderLine, UUID> {
    List<CommerceOrderLine> findByOrderIdOrderByCreatedAtAsc(UUID orderId);
}
