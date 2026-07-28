package com.flowledger.commerce.events;

import com.flowledger.commerce.publisher.model.CommerceChangeType;
import com.flowledger.platform.event.DomainEventPublisher;
import com.flowledger.search.event.SearchIndexDeleteEvent;
import com.flowledger.search.event.SearchIndexUpsertEvent;
import com.flowledger.search.model.SearchEntityType;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Translates ERP search-index events into {@link CommerceCatalogChangeEvent} notifications.
 * Publication is handled exclusively by {@link com.flowledger.commerce.publisher.CommercePublishingService}.
 */
@Component
public class MarketplacePublishEventBridge {
    private final DomainEventPublisher events;

    public MarketplacePublishEventBridge(DomainEventPublisher events) {
        this.events = events;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSearchUpsert(SearchIndexUpsertEvent event) {
        if (event.entityType() != SearchEntityType.PRODUCT) {
            return;
        }
        events.publish(new CommerceCatalogChangeEvent(
                this, event.organizationId(), null, CommerceChangeType.PRODUCT_UPSERT, null, event.entityId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSearchDelete(SearchIndexDeleteEvent event) {
        if (event.entityType() != SearchEntityType.PRODUCT) {
            return;
        }
        events.publish(new CommerceCatalogChangeEvent(
                this, event.organizationId(), null, CommerceChangeType.PRODUCT_DELETE, null, event.entityId()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPartnerCatalogSynced(PartnerCatalogSyncedEvent event) {
        events.publish(new CommerceCatalogChangeEvent(
                this,
                event.getOrganizationId(),
                null,
                CommerceChangeType.PARTNER_CATALOG_SYNC,
                null,
                event.getOrganizationId()));
    }
}
