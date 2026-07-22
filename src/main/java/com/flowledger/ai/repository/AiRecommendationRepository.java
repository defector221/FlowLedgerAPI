package com.flowledger.ai.repository;

import com.flowledger.ai.entity.AiRecommendation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiRecommendationRepository extends JpaRepository<AiRecommendation, UUID> {
    List<AiRecommendation> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    List<AiRecommendation> findByOrganizationIdAndStatusOrderByCreatedAtDesc(UUID organizationId, String status);

    Optional<AiRecommendation> findByIdAndOrganizationId(UUID id, UUID organizationId);

    boolean existsByOrganizationIdAndTypeAndRelatedEntityIdAndStatusIn(
            UUID organizationId, String type, UUID relatedEntityId, List<String> statuses);

    boolean existsByOrganizationIdAndTypeAndStatusInAndRelatedEntityIdIsNull(
            UUID organizationId, String type, List<String> statuses);
}
