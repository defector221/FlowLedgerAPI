package com.flowledger.commerce.publisher.sink;

import com.flowledger.commerce.marketplace.search.MarketplaceOpenSearchIndexService;
import com.flowledger.commerce.marketplace.search.MarketplaceSearchDocument;
import com.flowledger.commerce.publisher.model.BrandPublishedSnapshot;
import com.flowledger.commerce.publisher.model.CategoryPublishedSnapshot;
import com.flowledger.commerce.publisher.model.ProductPublishedSnapshot;
import com.flowledger.commerce.publisher.model.StorePublishedSnapshot;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Indexes commerce publish snapshots into the dedicated marketplace OpenSearch index. */
@Component
@Order(200)
public class MarketplaceOpenSearchPublishSink implements CommercePublishSink {
    private static final Logger log = LoggerFactory.getLogger(MarketplaceOpenSearchPublishSink.class);

    private final MarketplaceOpenSearchIndexService searchIndex;

    public MarketplaceOpenSearchPublishSink(MarketplaceOpenSearchIndexService searchIndex) {
        this.searchIndex = searchIndex;
    }

    @Override
    public void onStorePublished(StorePublishedSnapshot snapshot) {
        if (!searchIndex.isAvailable()) {
            return;
        }
        MarketplaceSearchDocument doc = MarketplaceSearchDocument.builder()
                .documentId(storeDocId(snapshot.storeId()))
                .entityType(MarketplaceSearchDocument.TYPE_STORE)
                .storeId(snapshot.storeId().toString())
                .title(snapshot.name())
                .searchText(join(snapshot.name(), snapshot.city(), snapshot.postalCode()))
                .city(snapshot.city())
                .postalCode(snapshot.postalCode())
                .visibility(snapshot.visibility())
                .published(true)
                .latitude(toDouble(snapshot.latitude()))
                .longitude(toDouble(snapshot.longitude()))
                .location(geoPoint(snapshot.latitude(), snapshot.longitude()))
                .updatedAt(OffsetDateTime.now().toString())
                .build();
        searchIndex.index(doc);
    }

    @Override
    public void onStoreUnpublished(UUID organizationId, UUID storeId) {
        if (!searchIndex.isAvailable()) {
            return;
        }
        searchIndex.delete(storeDocId(storeId));
    }

    @Override
    public void onProductPublished(ProductPublishedSnapshot snapshot) {
        if (!searchIndex.isAvailable()) {
            return;
        }
        MarketplaceSearchDocument doc = MarketplaceSearchDocument.builder()
                .documentId(productDocId(snapshot.storeId(), snapshot.productId()))
                .entityType(MarketplaceSearchDocument.TYPE_PRODUCT)
                .indexRowId(snapshot.indexId() != null ? snapshot.indexId().toString() : null)
                .storeId(snapshot.storeId().toString())
                .productId(snapshot.productId().toString())
                .title(snapshot.name())
                .searchText(join(
                        snapshot.name(),
                        snapshot.sku(),
                        snapshot.barcode(),
                        snapshot.brand(),
                        snapshot.categoryName()))
                .sku(snapshot.sku())
                .barcode(snapshot.barcode())
                .gtin(snapshot.gtin())
                .brand(snapshot.brand())
                .categoryId(snapshot.categoryId() != null ? snapshot.categoryId().toString() : null)
                .categoryName(snapshot.categoryName())
                .price(snapshot.price())
                .currency(snapshot.currency())
                .inventoryQty(snapshot.inventoryQty())
                .imageUrls(snapshot.imageUrls())
                .published(true)
                .updatedAt(OffsetDateTime.now().toString())
                .build();
        searchIndex.index(doc);
    }

    @Override
    public void onProductUnpublished(UUID organizationId, UUID storeId, UUID productId) {
        if (!searchIndex.isAvailable()) {
            return;
        }
        searchIndex.delete(productDocId(storeId, productId));
    }

    @Override
    public void onCategoryPublished(CategoryPublishedSnapshot snapshot) {
        log.debug("Category indexed for OpenSearch via product documents org={}", snapshot.organizationId());
    }

    @Override
    public void onBrandPublished(BrandPublishedSnapshot snapshot) {
        log.debug("Brand indexed for OpenSearch via product documents org={}", snapshot.organizationId());
    }

    static String storeDocId(UUID storeId) {
        return "mp:store:" + storeId;
    }

    static String productDocId(UUID storeId, UUID productId) {
        return "mp:product:" + storeId + ":" + productId;
    }

    private static Double toDouble(java.math.BigDecimal value) {
        return value != null ? value.doubleValue() : null;
    }

    private static Map<String, Double> geoPoint(java.math.BigDecimal lat, java.math.BigDecimal lon) {
        if (lat == null || lon == null) {
            return null;
        }
        return Map.of("lat", lat.doubleValue(), "lon", lon.doubleValue());
    }

    private static String join(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part != null && !part.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(part);
            }
        }
        return sb.toString();
    }
}
