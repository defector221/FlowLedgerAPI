package com.flowledger.commerce.order.repository;

import com.flowledger.commerce.order.entity.CommerceOrderReturn;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommerceOrderReturnRepository extends JpaRepository<CommerceOrderReturn, UUID> {
    List<CommerceOrderReturn> findByCommerceOrderIdOrderByCreatedAtDesc(UUID commerceOrderId);

    @Query(
            """
            select coalesce(sum(l.quantity), 0) from CommerceOrderReturnLine l
            join l.orderReturn r
            where l.orderLineId = :orderLineId
              and r.status = com.flowledger.commerce.order.domain.CommerceOrderReturnStatus.CONFIRMED
            """)
    java.math.BigDecimal sumReturnedQtyByOrderLineId(@Param("orderLineId") UUID orderLineId);
}
