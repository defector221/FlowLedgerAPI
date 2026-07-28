package com.flowledger.commerce.publisher;

import com.flowledger.commerce.events.CommerceCatalogChangeEvent;
import com.flowledger.commerce.marketplace.MarketplaceSyncService;
import com.flowledger.commerce.marketplace.domain.SyncMode;
import com.flowledger.commerce.publisher.model.CommerceChangeType;
import com.flowledger.commerce.store.entity.StoreCommerceProfile;
import com.flowledger.common.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Single entry point for commerce publishing. ERP modules, partner connectors, and event bridges
 * must route catalog changes through this service — never write marketplace tables directly.
 */
@Service
public class CommercePublishingService {
    private static final Logger log = LoggerFactory.getLogger(CommercePublishingService.class);

    private final CommercePublisher publisher;
    private final MarketplaceSyncService syncService;

    public CommercePublishingService(CommercePublisher publisher, MarketplaceSyncService syncService) {
        this.publisher = publisher;
        this.syncService = syncService;
    }

    public int publishStore(StoreCommerceProfile profile, UUID actorId) {
        return publisher.publishStore(profile, actorId);
    }

    public void unpublishStore(StoreCommerceProfile profile, UUID actorId) {
        publisher.unpublishStore(profile, actorId);
    }

    public void publishCatalogItem(UUID organizationId, UUID productId, UUID actorId) {
        publisher.publishCatalogItem(organizationId, productId, actorId);
    }

    public void publishInventoryItem(UUID organizationId, UUID storeId, UUID productId, BigDecimal qty, UUID actorId) {
        publisher.publishInventoryItem(organizationId, storeId, productId, qty, actorId);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCatalogChange(CommerceCatalogChangeEvent event) {
        try {
            TenantContext.set(event.getOrganizationId(), event.getActorId());
            switch (event.changeType()) {
                case PRODUCT_UPSERT -> publishCatalogItem(event.getOrganizationId(), event.productId(), event.getActorId());
                case PRODUCT_DELETE -> syncService.enqueue(
                        "PRODUCT_DELETE",
                        "PRODUCT",
                        event.getOrganizationId(),
                        event.productId(),
                        event.storeId(),
                        SyncMode.INCREMENTAL);
                case STORE_PUBLISH -> {
                    if (event.storeId() != null) {
                        syncService.syncStore(event.storeId(), SyncMode.FULL, event.getActorId());
                    }
                }
                case STORE_UNPUBLISH -> {
                    if (event.storeId() != null) {
                        syncService.syncStore(event.storeId(), SyncMode.FULL_REPUBLISH, event.getActorId());
                    }
                }
                case PARTNER_CATALOG_SYNC -> syncService.enqueue(
                        "PARTNER_SYNC",
                        "ORG",
                        event.getOrganizationId(),
                        event.getOrganizationId(),
                        null,
                        SyncMode.FULL_REPUBLISH);
            }
        } catch (Exception ex) {
            log.warn(
                    "Commerce catalog change failed type={} org={}: {}",
                    event.changeType(),
                    event.getOrganizationId(),
                    ex.getMessage());
            enqueueRetry(event);
        } finally {
            TenantContext.clear();
        }
    }

    private void enqueueRetry(CommerceCatalogChangeEvent event) {
        switch (event.changeType()) {
            case PRODUCT_UPSERT -> syncService.enqueue(
                    "PRODUCT_UPSERT",
                    "PRODUCT",
                    event.getOrganizationId(),
                    event.productId(),
                    event.storeId(),
                    SyncMode.INCREMENTAL);
            case PARTNER_CATALOG_SYNC -> syncService.enqueue(
                    "PARTNER_SYNC",
                    "ORG",
                    event.getOrganizationId(),
                    event.getOrganizationId(),
                    null,
                    SyncMode.FULL_REPUBLISH);
            default -> log.debug("No retry enqueue for change type {}", event.changeType());
        }
    }
}
