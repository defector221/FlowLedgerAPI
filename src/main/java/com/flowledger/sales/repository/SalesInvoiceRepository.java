package com.flowledger.sales.repository;

import com.flowledger.sales.entity.SalesInvoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalesInvoiceRepository extends JpaRepository<SalesInvoice, UUID> {
    Optional<SalesInvoice> findByIdAndOrganizationId(UUID id, UUID org);

    Optional<SalesInvoice> findByOrganizationIdAndSalesOrderId(UUID org, UUID orderId);

    Optional<SalesInvoice> findByOrganizationIdAndSalesOrderIdAndDeliveryChallanIdIsNull(UUID org, UUID orderId);

    Optional<SalesInvoice> findByOrganizationIdAndDeliveryChallanId(UUID org, UUID challanId);

    List<SalesInvoice> findByOrganizationIdOrderByInvoiceDateDesc(UUID org);
}
