package com.flowledger.sales.repository;

import com.flowledger.sales.entity.SalesInvoice;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SalesInvoiceRepository extends JpaRepository<SalesInvoice, UUID> {
    Optional<SalesInvoice> findByIdAndOrganizationId(UUID id, UUID org);

    Optional<SalesInvoice> findByOrganizationIdAndSalesOrderId(UUID org, UUID orderId);

    Optional<SalesInvoice> findByOrganizationIdAndSalesOrderIdAndDeliveryChallanIdIsNull(UUID org, UUID orderId);

    Optional<SalesInvoice> findByOrganizationIdAndDeliveryChallanId(UUID org, UUID challanId);

    List<SalesInvoice> findByOrganizationIdOrderByInvoiceDateDesc(UUID org);

    Page<SalesInvoice> findByOrganizationId(UUID org, Pageable pageable);
}
