package com.flowledger.marketing.repository;

import com.flowledger.marketing.entity.MarketingSequence;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketingSequenceRepository extends JpaRepository<MarketingSequence, UUID> {
    @EntityGraph(attributePaths = "steps")
    List<MarketingSequence> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    Optional<MarketingSequence> findByIdAndOrganizationId(UUID id, UUID organizationId);

    List<MarketingSequence> findByOrganizationIdAndTriggerTypeAndStatus(
            UUID organizationId, String triggerType, String status);

    @Query(
            "select distinct s from MarketingSequence s left join fetch s.steps where s.id = :id and s.organizationId = :orgId")
    Optional<MarketingSequence> findDetailedByIdAndOrganizationId(@Param("id") UUID id, @Param("orgId") UUID orgId);
}
