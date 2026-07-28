package com.flowledger.tax.repository;

import com.flowledger.tax.entity.TaxRuleAuditLog;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxRuleAuditLogRepository extends JpaRepository<TaxRuleAuditLog, UUID> {
    Optional<TaxRuleAuditLog> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<TaxRuleAuditLog> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    List<TaxRuleAuditLog> findByOrganizationIdAndTaxCategoryIdOrderByCreatedAtDesc(
            UUID organizationId, UUID taxCategoryId);
}
