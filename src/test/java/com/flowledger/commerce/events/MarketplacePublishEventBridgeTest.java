package com.flowledger.commerce.events;

import static org.mockito.Mockito.verify;

import com.flowledger.commerce.publisher.CommercePublishingService;
import com.flowledger.commerce.publisher.model.CommerceChangeType;
import com.flowledger.platform.event.DomainEventPublisher;
import com.flowledger.search.event.SearchIndexUpsertEvent;
import com.flowledger.search.model.SearchEntityType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketplacePublishEventBridgeTest {
    @Mock
    private DomainEventPublisher events;

    @InjectMocks
    private MarketplacePublishEventBridge bridge;

    @Test
    void emitsCatalogChangeEventForProductUpserts() {
        UUID orgId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        bridge.onSearchUpsert(new SearchIndexUpsertEvent(orgId, SearchEntityType.PRODUCT, productId));

        ArgumentCaptor<CommerceCatalogChangeEvent> captor = ArgumentCaptor.forClass(CommerceCatalogChangeEvent.class);
        verify(events).publish(captor.capture());
        CommerceCatalogChangeEvent event = captor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals(CommerceChangeType.PRODUCT_UPSERT, event.changeType());
        org.junit.jupiter.api.Assertions.assertEquals(productId, event.productId());
    }
}
