package com.flowledger.organization.repository;

import com.flowledger.organization.entity.OrganizationSettings;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationSettingsRepository extends JpaRepository<OrganizationSettings, UUID> {
    Optional<OrganizationSettings> findByOrganizationId(UUID organizationId);
}
