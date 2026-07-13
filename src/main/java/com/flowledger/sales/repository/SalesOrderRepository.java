package com.flowledger.sales.repository;

import com.flowledger.sales.entity.SalesOrder;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, UUID> {
    Optional<SalesOrder> findByIdAndOrganizationId(UUID id, UUID org);

    Optional<SalesOrder> findByOrganizationIdAndQuotationId(UUID org, UUID quotationId);
}
