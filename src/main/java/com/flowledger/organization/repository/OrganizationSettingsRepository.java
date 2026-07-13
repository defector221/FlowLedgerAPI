package com.flowledger.organization.repository;
import com.flowledger.organization.entity.OrganizationSettings; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface OrganizationSettingsRepository extends JpaRepository<OrganizationSettings,UUID> { Optional<OrganizationSettings> findByOrganizationId(UUID organizationId); }
