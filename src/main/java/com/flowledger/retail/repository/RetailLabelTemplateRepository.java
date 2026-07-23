package com.flowledger.retail.repository;

import com.flowledger.retail.entity.RetailLabelTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailLabelTemplateRepository extends JpaRepository<RetailLabelTemplate, UUID> {
    List<RetailLabelTemplate> findByOrganizationIdAndDeletedFalseOrderByNameAsc(UUID organizationId);

    Optional<RetailLabelTemplate> findByIdAndOrganizationIdAndDeletedFalse(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(UUID organizationId, String code);
}
