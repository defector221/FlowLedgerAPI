package com.flowledger.commerce.publisher.sink;

import com.flowledger.commerce.publisher.model.BrandPublishedSnapshot;
import com.flowledger.commerce.publisher.model.CategoryPublishedSnapshot;
import com.flowledger.commerce.publisher.model.ProductPublishedSnapshot;
import com.flowledger.commerce.publisher.model.StorePublishedSnapshot;
import java.util.UUID;

/**
 * Consumer-facing publish sink. CommercePublisher fans out normalized snapshots to every sink
 * (marketplace PG index, OpenSearch, future recommendation/analytics feeds).
 */
public interface CommercePublishSink {
    default void onStorePublished(StorePublishedSnapshot snapshot) {}

    default void onStoreUnpublished(UUID organizationId, UUID storeId) {}

    default void onProductPublished(ProductPublishedSnapshot snapshot) {}

    default void onProductUnpublished(UUID organizationId, UUID storeId, UUID productId) {}

    default void onCategoryPublished(CategoryPublishedSnapshot snapshot) {}

    default void onBrandPublished(BrandPublishedSnapshot snapshot) {}
}
