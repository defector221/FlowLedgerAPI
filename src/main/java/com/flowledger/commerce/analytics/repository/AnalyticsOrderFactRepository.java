package com.flowledger.commerce.analytics.repository;

import com.flowledger.commerce.analytics.entity.AnalyticsOrderFact;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsOrderFactRepository extends JpaRepository<AnalyticsOrderFact, UUID> {
    Optional<AnalyticsOrderFact> findByOrderId(UUID orderId);

    List<AnalyticsOrderFact> findByOrganizationIdAndPlacedAtBetweenOrderByPlacedAtDesc(
            UUID organizationId, OffsetDateTime from, OffsetDateTime to);
}
