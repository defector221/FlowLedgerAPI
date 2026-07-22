package com.flowledger.ai.repository;

import com.flowledger.ai.entity.AiEmbedding;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiEmbeddingRepository extends JpaRepository<AiEmbedding, UUID> {
    List<AiEmbedding> findByOrganizationIdAndSourceType(UUID organizationId, String sourceType);

    Optional<AiEmbedding> findByOrganizationIdAndSourceTypeAndSourceId(
            UUID organizationId, String sourceType, UUID sourceId);
}
