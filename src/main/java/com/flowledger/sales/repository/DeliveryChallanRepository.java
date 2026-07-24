package com.flowledger.sales.repository;

import com.flowledger.sales.entity.DeliveryChallan;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeliveryChallanRepository extends JpaRepository<DeliveryChallan, UUID> {
    Optional<DeliveryChallan> findByIdAndOrganizationId(UUID id, UUID org);

    @Query(
            """
            SELECT DISTINCT c FROM DeliveryChallan c
            LEFT JOIN FETCH c.items
            WHERE c.id = :id AND c.organizationId = :org
            """)
    Optional<DeliveryChallan> findDetailedByIdAndOrganizationId(@Param("id") UUID id, @Param("org") UUID org);

    Optional<DeliveryChallan> findByOrganizationIdAndSalesOrderId(UUID org, UUID orderId);

    Page<DeliveryChallan> findByOrganizationIdOrderByChallanDateDesc(UUID org, Pageable pageable);
}
