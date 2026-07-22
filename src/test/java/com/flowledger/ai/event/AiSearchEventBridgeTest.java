package com.flowledger.ai.event;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowledger.ai.recommendation.RecommendationGenerator;
import com.flowledger.search.event.SearchIndexUpsertEvent;
import com.flowledger.search.model.SearchEntityType;
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

    @InjectMocks
    AiSearchEventBridge bridge;

    @Test
    void productUpsertTriggersInventoryHeuristics() {
        UUID org = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(recommendationGenerator.onProductChanged(productId)).thenReturn(1);

        bridge.onUpsert(new SearchIndexUpsertEvent(org, SearchEntityType.PRODUCT, productId));

        verify(recommendationGenerator).onProductChanged(productId);
        verify(lifecycleEvents).recommendationSeed(eq(org), eq("PRODUCT"), eq(productId));
    }

    @Test
    void customerUpsertTriggersCreditHeuristics() {
        UUID org = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(recommendationGenerator.onCustomerChanged(customerId)).thenReturn(0);

        bridge.onUpsert(new SearchIndexUpsertEvent(org, SearchEntityType.CUSTOMER, customerId));

        verify(recommendationGenerator).onCustomerChanged(customerId);
        verify(lifecycleEvents).recommendationSeed(eq(org), eq("CUSTOMER"), eq(customerId));
    }

    @Test
    void supplierUpsertDoesNotCallInventoryHeuristics() {
        UUID org = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();

        bridge.onUpsert(new SearchIndexUpsertEvent(org, SearchEntityType.SUPPLIER, supplierId));

        verify(recommendationGenerator, never()).onProductChanged(any());
        verify(recommendationGenerator, never()).onCustomerChanged(any());
        verify(lifecycleEvents)
                .publish(eq(org), eq(AiLifecycleEvent.RECOMMENDATION_SEED), eq("SUPPLIER"), eq(supplierId), any());
    }
}
