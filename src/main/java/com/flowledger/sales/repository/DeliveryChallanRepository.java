package com.flowledger.sales.repository;

import com.flowledger.sales.entity.DeliveryChallan;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryChallanRepository extends JpaRepository<DeliveryChallan, UUID> {
    Optional<DeliveryChallan> findByIdAndOrganizationId(UUID id, UUID org);

    Optional<DeliveryChallan> findByOrganizationIdAndSalesOrderId(UUID org, UUID orderId);
}
