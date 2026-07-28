package com.flowledger.commerce.marketplace.search;

import com.flowledger.commerce.config.CommerceProperties;
import com.flowledger.commerce.marketplace.domain.MarketplaceProduct;
import com.flowledger.commerce.marketplace.domain.MarketplaceStore;
import com.flowledger.commerce.marketplace.search.MarketplaceOpenSearchIndexService.MarketplaceSearchQuery;
import com.flowledger.commerce.marketplace.search.MarketplaceOpenSearchIndexService.MarketplaceSearchResult;
import com.flowledger.search.exception.SearchUnavailableException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "flowledger.commerce.marketplace.search.backend", havingValue = "opensearch")
public class OpenSearchMarketplaceSearchBackend implements MarketplaceSearchBackend {
    private final MarketplaceOpenSearchIndexService searchIndex;
    private final PostgresMarketplaceSearchBackend fallback;
    private final CommerceProperties properties;

    public OpenSearchMarketplaceSearchBackend(
            MarketplaceOpenSearchIndexService searchIndex,
            PostgresMarketplaceSearchBackend fallback,
            CommerceProperties properties) {
        this.searchIndex = searchIndex;
        this.fallback = fallback;
        this.properties = properties;
    }

    @Override
    public Page<MarketplaceStore> searchStores(StoreSearchCriteria criteria, Pageable pageable) {
        if (!searchIndex.isAvailable()) {
            return fallback.searchStores(criteria, pageable);
        }
        try {
            MarketplaceSearchQuery query = new MarketplaceSearchQuery(
                    MarketplaceSearchDocument.TYPE_STORE,
                    criteria.q(),
                    criteria.city(),
                    criteria.pincode(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    toDouble(criteria.lat()),
                    toDouble(criteria.lng()),
                    toDouble(criteria.radiusKm()),
                    (int) pageable.getOffset(),
                    pageable.getPageSize());
            MarketplaceSearchResult result = searchIndex.search(query);
            List<MarketplaceStore> stores = new ArrayList<>();
            for (MarketplaceSearchDocument doc : result.documents()) {
                if (!isVisibleInSearch(doc.getVisibility())) {
                    continue;
                }
                stores.add(toStore(doc));
            }
            if (stores.isEmpty() && hasAnyPublishedStores(criteria)) {
                return fallback.searchStores(criteria, pageable);
            }
            stores.sort(Comparator.comparing(s -> s.distanceKm() != null ? s.distanceKm() : Double.MAX_VALUE));
            return new PageImpl<>(stores, pageable, result.total());
        } catch (SearchUnavailableException ex) {
            return fallback.searchStores(criteria, pageable);
        }
    }

    private boolean hasAnyPublishedStores(StoreSearchCriteria criteria) {
        Page<MarketplaceStore> pg = fallback.searchStores(criteria, PageRequest.of(0, 1));
        return !pg.isEmpty();
    }

    private boolean isVisibleInSearch(String visibility) {
        if (!properties.getMarketplace().getSearch().isPublicVisibilityRequired()) {
            return true;
        }
        return "PUBLIC".equals(visibility);
    }

    @Override
    public Page<MarketplaceProduct> searchProducts(ProductSearchCriteria criteria, Pageable pageable) {
        if (!searchIndex.isAvailable()) {
            return fallback.searchProducts(criteria, pageable);
        }
        try {
            MarketplaceSearchQuery query = new MarketplaceSearchQuery(
                    MarketplaceSearchDocument.TYPE_PRODUCT,
                    criteria.q(),
                    null,
                    null,
                    criteria.barcode(),
                    criteria.sku(),
                    criteria.brand(),
                    criteria.categoryId() != null ? criteria.categoryId().toString() : null,
                    criteria.storeId() != null ? criteria.storeId().toString() : null,
                    toDouble(criteria.nearLat()),
                    toDouble(criteria.nearLng()),
                    toDouble(criteria.radiusKm()),
                    (int) pageable.getOffset(),
                    pageable.getPageSize());
            MarketplaceSearchResult result = searchIndex.search(query);
            List<MarketplaceProduct> products =
                    result.documents().stream().map(this::toProduct).toList();
            if (products.isEmpty() && hasAnyPublishedProducts(criteria)) {
                return fallback.searchProducts(criteria, pageable);
            }
            return new PageImpl<>(products, pageable, result.total());
        } catch (SearchUnavailableException ex) {
            return fallback.searchProducts(criteria, pageable);
        }
    }

    private boolean hasAnyPublishedProducts(ProductSearchCriteria criteria) {
        Page<MarketplaceProduct> pg = fallback.searchProducts(criteria, PageRequest.of(0, 1));
        return !pg.isEmpty();
    }

    @Override
    public Optional<MarketplaceProduct> findByBarcode(String barcode) {
        if (!searchIndex.isAvailable()) {
            return fallback.findByBarcode(barcode);
        }
        MarketplaceSearchQuery query = new MarketplaceSearchQuery(
                MarketplaceSearchDocument.TYPE_PRODUCT,
                null,
                null,
                null,
                barcode,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                1);
        MarketplaceSearchResult result = searchIndex.search(query);
        return result.documents().stream().findFirst().map(this::toProduct);
    }

    @Override
    public List<MarketplaceStore> findStoresNearProduct(UUID productId, GeoCriteria geo) {
        if (!searchIndex.isAvailable()) {
            return fallback.findStoresNearProduct(productId, geo);
        }
        MarketplaceSearchQuery productQuery = new MarketplaceSearchQuery(
                MarketplaceSearchDocument.TYPE_PRODUCT,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                100);
        // Postgres fallback handles multi-store inventory join reliably
        return fallback.findStoresNearProduct(productId, geo);
    }

    private MarketplaceStore toStore(MarketplaceSearchDocument doc) {
        UUID indexId = parseUuid(doc.getIndexRowId());
        UUID storeId = parseUuid(doc.getStoreId());
        return new MarketplaceStore(
                indexId != null ? indexId : storeId,
                storeId,
                doc.getTitle(),
                doc.getCity(),
                doc.getPostalCode(),
                null,
                null,
                doc.getLatitude() != null ? BigDecimal.valueOf(doc.getLatitude()) : null,
                doc.getLongitude() != null ? BigDecimal.valueOf(doc.getLongitude()) : null,
                null,
                doc.getVisibility(),
                false,
                false,
                false,
                false,
                null,
                Collections.emptyMap());
    }

    private MarketplaceProduct toProduct(MarketplaceSearchDocument doc) {
        UUID indexId = parseUuid(doc.getIndexRowId());
        return new MarketplaceProduct(
                indexId != null ? indexId : UUID.randomUUID(),
                parseUuid(doc.getStoreId()),
                parseUuid(doc.getProductId()),
                doc.getSku(),
                doc.getBarcode(),
                doc.getGtin(),
                doc.getTitle(),
                null,
                doc.getBrand(),
                parseUuid(doc.getCategoryId()),
                doc.getCategoryName(),
                doc.getPrice(),
                doc.getCurrency(),
                doc.getInventoryQty(),
                doc.getImageUrls() != null ? doc.getImageUrls() : List.of(),
                Map.of());
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static Double toDouble(BigDecimal value) {
        return value != null ? value.doubleValue() : null;
    }
}
