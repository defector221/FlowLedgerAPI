package com.flowledger.migration.repository;

import com.flowledger.migration.domain.ImportJobStatus;
import com.flowledger.migration.entity.ImportJob;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportJobRepository extends JpaRepository<ImportJob, UUID> {
    Optional<ImportJob> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Page<ImportJob> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    List<ImportJob> findByOrganizationIdAndStatus(UUID organizationId, ImportJobStatus status);
}
