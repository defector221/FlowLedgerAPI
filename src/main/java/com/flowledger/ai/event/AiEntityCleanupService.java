package com.flowledger.ai.event;

import com.flowledger.ai.config.ConditionalOnAiEnabled;
import com.flowledger.ai.repository.AiEmbeddingRepository;
import com.flowledger.ai.repository.AiRecommendationRepository;
import com.flowledger.search.model.SearchEntityType;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Removes AI artifacts tied to a deleted or rolled-back search entity. */
@Service
@ConditionalOnAiEnabled
public class AiEntityCleanupService {
    private final AiRecommendationRepository recommendations;
    private final AiEmbeddingRepository embeddings;

    public AiEntityCleanupService(AiRecommendationRepository recommendations, AiEmbeddingRepository embeddings) {
        this.recommendations = recommendations;
        this.embeddings = embeddings;
    }

    @Transactional
    public int cleanupEntity(UUID organizationId, SearchEntityType entityType, UUID entityId) {
        int removed = 0;
        removed += recommendations.deleteByOrganizationIdAndRelatedEntityTypeAndRelatedEntityId(
                organizationId, entityType.name(), entityId);
        removed +=
                embeddings.deleteByOrganizationIdAndSourceTypeAndSourceId(organizationId, entityType.name(), entityId);
        return removed;
    }
}
