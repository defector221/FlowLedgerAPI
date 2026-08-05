package com.flowledger.ai.repository;

import com.flowledger.ai.entity.AiAutomation;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiAutomationRepository extends JpaRepository<AiAutomation, UUID> {
    List<AiAutomation> findByOrganizationIdOrderByUpdatedAtDesc(UUID organizationId);

    Optional<AiAutomation> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<AiAutomation> findByStatusAndTriggerTypeAndNextRunAtLessThanEqual(
            String status, String triggerType, OffsetDateTime when);

    List<AiAutomation> findByStatusAndTriggerTypeAndEventType(String status, String triggerType, String eventType);
}
