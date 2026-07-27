package com.flowledger.migration.repository;

import com.flowledger.migration.entity.ExportJob;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExportJobRepository extends JpaRepository<ExportJob, UUID> {
    Optional<ExportJob> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Page<ExportJob> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);
}
