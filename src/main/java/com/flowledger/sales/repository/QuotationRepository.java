package com.flowledger.sales.repository;

import com.flowledger.sales.entity.Quotation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuotationRepository extends JpaRepository<Quotation, UUID> {
    Optional<Quotation> findByIdAndOrganizationId(UUID id, UUID org);

    List<Quotation> findByOrganizationIdOrderByQuotationDateDesc(UUID org);
}
