package com.flowledger.ai.repository;

import com.flowledger.ai.entity.AiUsageBudget;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiUsageBudgetRepository extends JpaRepository<AiUsageBudget, UUID> {
    Optional<AiUsageBudget> findByOrganizationId(UUID organizationId);
}
