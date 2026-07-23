package com.flowledger.platform.repository;

import com.flowledger.platform.entity.OrganizationFeature;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationFeatureRepository extends JpaRepository<OrganizationFeature, UUID> {
    List<OrganizationFeature> findByOrganizationId(UUID organizationId);

    Optional<OrganizationFeature> findByOrganizationIdAndModuleCodeAndFeatureCode(
            UUID organizationId, String moduleCode, String featureCode);

    List<OrganizationFeature> findByOrganizationIdAndModuleCode(UUID organizationId, String moduleCode);
}
