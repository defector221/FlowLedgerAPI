package com.flowledger.commerce.events;

import java.util.UUID;

public class ProductPublishedEvent extends CommerceDomainEvent {
    private final UUID storeId;
    private final UUID productId;

    public ProductPublishedEvent(
            Object source, UUID organizationId, UUID actorId, UUID storeId, UUID productId) {
        super(source, organizationId, actorId, "MarketplaceProductIndex", productId);
        this.storeId = storeId;
        this.productId = productId;
    }

    public UUID storeId() {
        return storeId;
    }

    public UUID productId() {
        return productId;
    }
}
