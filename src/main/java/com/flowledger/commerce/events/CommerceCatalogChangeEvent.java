package com.flowledger.commerce.events;

import com.flowledger.commerce.publisher.model.CommerceChangeType;
import java.util.UUID;

/**
 * Internal commerce-layer change notification. ERP and partner connectors emit upstream events;
 * bridges translate them into this event. Only {@link com.flowledger.commerce.publisher.CommercePublishingService}
 * handles publication — modules must never write marketplace tables directly.
 */
public class CommerceCatalogChangeEvent extends CommerceDomainEvent {
    private final CommerceChangeType changeType;
    private final UUID storeId;
    private final UUID productId;

    public CommerceCatalogChangeEvent(
            Object source,
            UUID organizationId,
            UUID actorId,
            CommerceChangeType changeType,
            UUID storeId,
            UUID productId) {
        super(source, organizationId, actorId, changeType.name(), productId != null ? productId : storeId);
        this.changeType = changeType;
        this.storeId = storeId;
        this.productId = productId;
    }

    public CommerceChangeType changeType() {
        return changeType;
    }

    public UUID storeId() {
        return storeId;
    }

    public UUID productId() {
        return productId;
    }
}
