package com.flowledger.platform.repository;

import com.flowledger.platform.entity.OrganizationModule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationModuleRepository extends JpaRepository<OrganizationModule, UUID> {
    List<OrganizationModule> findByOrganizationId(UUID organizationId);

    Optional<OrganizationModule> findByOrganizationIdAndModuleCode(UUID organizationId, String moduleCode);
}
