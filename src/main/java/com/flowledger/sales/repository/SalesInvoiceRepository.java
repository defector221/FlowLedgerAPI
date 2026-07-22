package com.flowledger.sales.repository;

import com.flowledger.sales.entity.SalesInvoice;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SalesInvoiceRepository extends JpaRepository<SalesInvoice, UUID> {
    Optional<SalesInvoice> findByIdAndOrganizationId(UUID id, UUID org);

    @Query(
            """
            select distinct i from SalesInvoice i
            left join fetch i.items
            where i.id = :id and i.organizationId = :org
            """)
    Optional<SalesInvoice> findDetailedByIdAndOrganizationId(@Param("id") UUID id, @Param("org") UUID org);

    Optional<SalesInvoice> findByOrganizationIdAndSalesOrderId(UUID org, UUID orderId);

    Optional<SalesInvoice> findByOrganizationIdAndSalesOrderIdAndDeliveryChallanIdIsNull(UUID org, UUID orderId);

    Optional<SalesInvoice> findByOrganizationIdAndDeliveryChallanId(UUID org, UUID challanId);

    List<SalesInvoice> findByOrganizationIdOrderByInvoiceDateDesc(UUID org);

    Page<SalesInvoice> findByOrganizationId(UUID org, Pageable pageable);

    long countByOrganizationIdAndInvoiceDateBetween(UUID organizationId, LocalDate start, LocalDate end);

    @Query(
            value =
                    """
                    SELECT sii.product_id, COALESCE(SUM(sii.quantity), 0)
                    FROM sales_invoice_items sii
                    INNER JOIN sales_invoices si ON si.id = sii.sales_invoice_id
                    INNER JOIN products p ON p.id = sii.product_id
                    WHERE si.organization_id = :org
                      AND si.status = 'DRAFT'
                      AND (p.item_type IS NULL OR p.item_type = '' OR upper(p.item_type) = 'PRODUCT')
                    GROUP BY sii.product_id
                    """,
            nativeQuery = true)
    List<Object[]> sumDraftQuantitiesByProduct(@Param("org") UUID org);
}
