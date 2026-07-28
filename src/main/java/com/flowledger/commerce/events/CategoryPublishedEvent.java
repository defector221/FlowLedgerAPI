package com.flowledger.commerce.events;

import java.util.UUID;

public class CategoryPublishedEvent extends CommerceDomainEvent {
    private final UUID categoryId;

    public CategoryPublishedEvent(Object source, UUID organizationId, UUID actorId, UUID categoryId) {
        super(source, organizationId, actorId, "MarketplaceCategoryIndex", categoryId);
        this.categoryId = categoryId;
    }

    public UUID categoryId() {
        return categoryId;
    }
}
