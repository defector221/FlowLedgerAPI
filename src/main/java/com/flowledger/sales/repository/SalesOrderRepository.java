package com.flowledger.sales.repository;

import com.flowledger.sales.entity.SalesOrder;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, UUID> {
    Optional<SalesOrder> findByIdAndOrganizationId(UUID id, UUID org);

    @Query(
            """
            SELECT DISTINCT o FROM SalesOrder o
            LEFT JOIN FETCH o.items
            WHERE o.id = :id AND o.organizationId = :org
            """)
    Optional<SalesOrder> findDetailedByIdAndOrganizationId(@Param("id") UUID id, @Param("org") UUID org);

    Optional<SalesOrder> findByOrganizationIdAndQuotationId(UUID org, UUID quotationId);

    Page<SalesOrder> findByOrganizationIdOrderByOrderDateDesc(UUID org, Pageable pageable);
}
