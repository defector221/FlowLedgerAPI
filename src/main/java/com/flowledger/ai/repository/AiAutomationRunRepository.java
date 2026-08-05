package com.flowledger.ai.repository;

import com.flowledger.ai.entity.AiAutomationRun;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiAutomationRunRepository extends JpaRepository<AiAutomationRun, UUID> {
    List<AiAutomationRun> findByAutomationIdOrderByStartedAtDesc(UUID automationId);

    List<AiAutomationRun> findByOrganizationIdOrderByStartedAtDesc(UUID organizationId);
}
