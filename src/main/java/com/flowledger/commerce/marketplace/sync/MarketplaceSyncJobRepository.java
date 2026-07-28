package com.flowledger.commerce.marketplace.sync;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MarketplaceSyncJobRepository extends JpaRepository<MarketplaceSyncJob, UUID> {
    Optional<MarketplaceSyncJob> findByIdempotencyKey(String idempotencyKey);

    Optional<MarketplaceSyncJob> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<MarketplaceSyncJob> findTop10ByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    @Query(
            """
            SELECT j FROM MarketplaceSyncJob j
            WHERE j.status IN ('PENDING', 'FAILED')
              AND j.scheduledAt <= :now
              AND j.attempts < j.maxAttempts
            ORDER BY j.scheduledAt ASC
            """)
    List<MarketplaceSyncJob> findDueJobs(OffsetDateTime now);
}
