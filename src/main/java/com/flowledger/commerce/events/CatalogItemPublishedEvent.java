package com.flowledger.commerce.events;

import java.util.UUID;

/** @deprecated use {@link ProductPublishedEvent} */
@Deprecated
public class CatalogItemPublishedEvent extends ProductPublishedEvent {
    public CatalogItemPublishedEvent(
            Object source, UUID organizationId, UUID actorId, UUID storeId, UUID productId) {
        super(source, organizationId, actorId, storeId, productId);
    }
}
