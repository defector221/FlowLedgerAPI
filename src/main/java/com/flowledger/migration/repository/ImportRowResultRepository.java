package com.flowledger.migration.repository;

import com.flowledger.migration.domain.ImportRowStatus;
import com.flowledger.migration.entity.ImportRowResult;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ImportRowResultRepository extends JpaRepository<ImportRowResult, UUID> {
    Page<ImportRowResult> findByJobIdAndOrganizationIdOrderByRowNumberAsc(
            UUID jobId, UUID organizationId, Pageable pageable);

    List<ImportRowResult> findByJobIdAndOrganizationIdAndStatusInOrderByRowNumberAsc(
            UUID jobId, UUID organizationId, List<ImportRowStatus> statuses);

    Optional<ImportRowResult> findByJobIdAndRowNumberAndOrganizationId(UUID jobId, int rowNumber, UUID organizationId);

    long countByJobIdAndStatus(UUID jobId, ImportRowStatus status);

    @Modifying
    @Query("delete from ImportRowResult r where r.jobId = :jobId")
    void deleteByJobId(UUID jobId);
}
