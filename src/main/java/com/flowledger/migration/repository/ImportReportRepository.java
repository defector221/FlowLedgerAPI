package com.flowledger.migration.repository;

import com.flowledger.migration.entity.ImportReport;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImportReportRepository extends JpaRepository<ImportReport, UUID> {
    Optional<ImportReport> findByJobIdAndOrganizationId(UUID jobId, UUID organizationId);
}
