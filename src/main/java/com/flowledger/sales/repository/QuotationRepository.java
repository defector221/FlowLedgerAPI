package com.flowledger.sales.repository;

import com.flowledger.sales.entity.Quotation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuotationRepository extends JpaRepository<Quotation, UUID> {
    Optional<Quotation> findByIdAndOrganizationId(UUID id, UUID org);

    @Query(
            """
            SELECT DISTINCT q FROM Quotation q
            LEFT JOIN FETCH q.items
            WHERE q.id = :id AND q.organizationId = :org
            """)
    Optional<Quotation> findDetailedByIdAndOrganizationId(@Param("id") UUID id, @Param("org") UUID org);

    Page<Quotation> findByOrganizationIdOrderByQuotationDateDesc(UUID org, Pageable pageable);
}
