package com.flowledger.ai.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowledger.ai.recommendation.RecommendationGenerator;
import com.flowledger.search.event.SearchIndexDeleteEvent;
import com.flowledger.search.event.SearchIndexUpsertEvent;
import com.flowledger.search.model.SearchDocument;
import com.flowledger.search.model.SearchEntityType;
import com.flowledger.search.service.SearchEntityDocumentLoader;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiSearchEventBridgeTest {
    @Mock
    RecommendationGenerator recommendationGenerator;

    @Mock
    AiLifecycleEventPublisher lifecycleEvents;

    @Mock
    SearchEntityDocumentLoader documentLoader;

    @Mock
    AiEntityCleanupService cleanupService;

    @InjectMocks
    AiSearchEventBridge bridge;

    @Test
    void productUpsertTriggersInventoryHeuristics() {
        UUID org = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(documentLoader.load(org, SearchEntityType.PRODUCT, productId)).thenReturn(new SearchDocument());
        when(recommendationGenerator.onProductChanged(productId)).thenReturn(1);

        bridge.onUpsert(new SearchIndexUpsertEvent(org, SearchEntityType.PRODUCT, productId));

        verify(recommendationGenerator).onProductChanged(productId);
        verify(lifecycleEvents).recommendationSeed(eq(org), eq("PRODUCT"), eq(productId));
        verify(cleanupService, never()).cleanupEntity(any(), any(), any());
    }

    @Test
    void customerUpsertTriggersCreditHeuristics() {
        UUID org = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(documentLoader.load(org, SearchEntityType.CUSTOMER, customerId)).thenReturn(new SearchDocument());
        when(recommendationGenerator.onCustomerChanged(customerId)).thenReturn(0);

        bridge.onUpsert(new SearchIndexUpsertEvent(org, SearchEntityType.CUSTOMER, customerId));

        verify(recommendationGenerator).onCustomerChanged(customerId);
        verify(lifecycleEvents).recommendationSeed(eq(org), eq("CUSTOMER"), eq(customerId));
        verify(cleanupService, never()).cleanupEntity(any(), any(), any());
    }

    @Test
    void supplierUpsertDoesNotCallInventoryHeuristics() {
        UUID org = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        when(documentLoader.load(org, SearchEntityType.SUPPLIER, supplierId)).thenReturn(new SearchDocument());

        bridge.onUpsert(new SearchIndexUpsertEvent(org, SearchEntityType.SUPPLIER, supplierId));

        verify(recommendationGenerator, never()).onProductChanged(any());
        verify(recommendationGenerator, never()).onCustomerChanged(any());
        verify(lifecycleEvents)
                .publish(eq(org), eq(AiLifecycleEvent.RECOMMENDATION_SEED), eq("SUPPLIER"), eq(supplierId), any());
        verify(cleanupService, never()).cleanupEntity(any(), any(), any());
    }

    @Test
    void missingEntityOnUpsertTriggersCleanup() {
        UUID org = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(documentLoader.load(org, SearchEntityType.PRODUCT, productId)).thenReturn(null);
        when(cleanupService.cleanupEntity(org, SearchEntityType.PRODUCT, productId))
                .thenReturn(2);

        bridge.onUpsert(new SearchIndexUpsertEvent(org, SearchEntityType.PRODUCT, productId));

        verify(cleanupService).cleanupEntity(org, SearchEntityType.PRODUCT, productId);
        verify(recommendationGenerator, never()).onProductChanged(any());
        verify(lifecycleEvents, never()).recommendationSeed(any(), any(), any());
    }

    @Test
    void deleteTriggersCleanup() {
        UUID org = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(cleanupService.cleanupEntity(org, SearchEntityType.CUSTOMER, customerId))
                .thenReturn(1);

        bridge.onDelete(new SearchIndexDeleteEvent(org, SearchEntityType.CUSTOMER, customerId));

        verify(cleanupService).cleanupEntity(org, SearchEntityType.CUSTOMER, customerId);
        verify(lifecycleEvents, never()).publish(any(), any(), any(), any(), any());
    }
}
