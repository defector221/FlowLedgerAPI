package com.flowledger.commerce.analytics.repository;

import com.flowledger.commerce.analytics.entity.AnalyticsFunnelEvent;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnalyticsFunnelEventRepository extends JpaRepository<AnalyticsFunnelEvent, UUID> {
    long countByOrganizationIdAndEventTypeAndOccurredAtBetween(
            UUID organizationId, String eventType, OffsetDateTime from, OffsetDateTime to);
}
