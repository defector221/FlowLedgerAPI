package com.flowledger.sales.repository; import com.flowledger.sales.entity.SalesOrder; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface SalesOrderRepository extends JpaRepository<SalesOrder,UUID>{Optional<SalesOrder> findByIdAndOrganizationId(UUID id,UUID org);Optional<SalesOrder> findByOrganizationIdAndQuotationId(UUID org,UUID quotationId);}
