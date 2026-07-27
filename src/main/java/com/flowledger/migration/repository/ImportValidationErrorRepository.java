package com.flowledger.migration.repository;

import com.flowledger.migration.entity.ImportValidationError;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ImportValidationErrorRepository extends JpaRepository<ImportValidationError, UUID> {
    List<ImportValidationError> findByJobIdAndOrganizationIdOrderByRowNumberAsc(UUID jobId, UUID organizationId);

    List<ImportValidationError> findByJobIdAndRowNumber(UUID jobId, int rowNumber);

    @Modifying
    @Query("delete from ImportValidationError e where e.jobId = :jobId")
    void deleteByJobId(UUID jobId);
}
