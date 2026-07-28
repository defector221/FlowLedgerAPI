package com.flowledger.commerce.events;

import java.util.UUID;

public class InventoryPublishedEvent extends CommerceDomainEvent {
    private final UUID storeId;
    private final UUID productId;

    public InventoryPublishedEvent(
            Object source, UUID organizationId, UUID actorId, UUID storeId, UUID productId) {
        super(source, organizationId, actorId, "MarketplaceInventoryIndex", productId);
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
