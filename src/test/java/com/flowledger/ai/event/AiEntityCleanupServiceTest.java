package com.flowledger.ai.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowledger.ai.repository.AiEmbeddingRepository;
import com.flowledger.ai.repository.AiRecommendationRepository;
import com.flowledger.search.model.SearchEntityType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiEntityCleanupServiceTest {
    @Mock
    AiRecommendationRepository recommendations;

    @Mock
    AiEmbeddingRepository embeddings;

    @InjectMocks
    AiEntityCleanupService service;

    @Test
    void cleanupEntityRemovesRecommendationsAndEmbeddings() {
        UUID org = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(recommendations.deleteByOrganizationIdAndRelatedEntityTypeAndRelatedEntityId(org, "PRODUCT", productId))
                .thenReturn(1);
        when(embeddings.deleteByOrganizationIdAndSourceTypeAndSourceId(org, "PRODUCT", productId))
                .thenReturn(1);

        int removed = service.cleanupEntity(org, SearchEntityType.PRODUCT, productId);

        assertThat(removed).isEqualTo(2);
        verify(recommendations).deleteByOrganizationIdAndRelatedEntityTypeAndRelatedEntityId(org, "PRODUCT", productId);
        verify(embeddings).deleteByOrganizationIdAndSourceTypeAndSourceId(org, "PRODUCT", productId);
    }
}
