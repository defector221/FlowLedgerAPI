package com.flowledger.retail.repository;

import com.flowledger.retail.entity.RetailCommissionRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailCommissionRuleRepository extends JpaRepository<RetailCommissionRule, UUID> {
    List<RetailCommissionRule> findByOrganizationIdAndDeletedFalseOrderByNameAsc(UUID organizationId);

    Optional<RetailCommissionRule> findByIdAndOrganizationIdAndDeletedFalse(UUID id, UUID organizationId);
}
