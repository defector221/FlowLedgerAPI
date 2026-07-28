package com.flowledger.commerce.connector.repository;

import com.flowledger.commerce.connector.entity.CommerceConnectorSyncJob;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CommerceConnectorSyncJobRepository extends JpaRepository<CommerceConnectorSyncJob, UUID> {
    @Query("SELECT j FROM CommerceConnectorSyncJob j WHERE j.status = 'PENDING' AND j.scheduledAt <= CURRENT_TIMESTAMP ORDER BY j.scheduledAt ASC")
    List<CommerceConnectorSyncJob> findDueJobs(Pageable pageable);

    List<CommerceConnectorSyncJob> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
}
