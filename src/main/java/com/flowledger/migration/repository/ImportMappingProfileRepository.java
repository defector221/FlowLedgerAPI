package com.flowledger.migration.repository;

import com.flowledger.migration.entity.ImportMappingProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportMappingProfileRepository extends JpaRepository<ImportMappingProfile, UUID> {
    List<ImportMappingProfile> findByOrganizationIdAndActiveTrueOrderByNameAsc(UUID organizationId);

    List<ImportMappingProfile> findByOrganizationIdAndModuleAndActiveTrueOrderByNameAsc(
            UUID organizationId, String module);

    Optional<ImportMappingProfile> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
