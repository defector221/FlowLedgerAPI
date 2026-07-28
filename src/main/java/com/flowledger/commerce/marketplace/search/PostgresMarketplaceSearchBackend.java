package com.flowledger.commerce.marketplace.search;

import com.flowledger.commerce.config.CommerceProperties;
import com.flowledger.commerce.marketplace.domain.MarketplaceProduct;
import com.flowledger.commerce.marketplace.domain.MarketplaceStore;
import com.flowledger.commerce.marketplace.mapper.MarketplaceIndexMapper;
import com.flowledger.commerce.publisher.entity.MarketplaceInventoryIndex;
import com.flowledger.commerce.publisher.entity.MarketplaceProductIndex;
import com.flowledger.commerce.publisher.entity.MarketplaceStoreIndex;
import com.flowledger.commerce.publisher.repository.MarketplaceInventoryIndexRepository;
import com.flowledger.commerce.publisher.repository.MarketplaceProductIndexRepository;
import com.flowledger.commerce.publisher.repository.MarketplaceStoreIndexRepository;
import com.flowledger.common.dto.PageResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

@Component
public class PostgresMarketplaceSearchBackend implements MarketplaceSearchBackend {
    private final MarketplaceStoreIndexRepository storeIndexRepository;
    private final MarketplaceProductIndexRepository productIndexRepository;
    private final MarketplaceInventoryIndexRepository inventoryIndexRepository;
    private final MarketplaceIndexMapper mapper;
    private final CommerceProperties properties;

    public PostgresMarketplaceSearchBackend(
            MarketplaceStoreIndexRepository storeIndexRepository,
            MarketplaceProductIndexRepository productIndexRepository,
            MarketplaceInventoryIndexRepository inventoryIndexRepository,
            MarketplaceIndexMapper mapper,
            CommerceProperties properties) {
        this.storeIndexRepository = storeIndexRepository;
        this.productIndexRepository = productIndexRepository;
        this.inventoryIndexRepository = inventoryIndexRepository;
        this.mapper = mapper;
        this.properties = properties;
    }

    @Override
    public Page<MarketplaceStore> searchStores(StoreSearchCriteria criteria, Pageable pageable) {
        List<MarketplaceStoreIndex> all = storeIndexRepository.findAll().stream()
                .filter(MarketplaceStoreIndex::isPublished)
                .filter(this::isVisibleInSearch)
                .filter(s -> matchesStore(criteria, s))
                .toList();

        List<MarketplaceStore> mapped = new ArrayList<>();
        for (MarketplaceStoreIndex index : all) {
            Double distance = distanceKm(criteria.lat(), criteria.lng(), index.getLatitude(), index.getLongitude());
            if (criteria.radiusKm() != null
                    && criteria.lat() != null
                    && criteria.lng() != null
                    && distance != null
                    && distance > criteria.radiusKm().doubleValue()) {
                continue;
            }
            mapped.add(mapper.toStore(index, distance));
        }
        mapped.sort(Comparator.comparing(s -> s.distanceKm() != null ? s.distanceKm() : Double.MAX_VALUE));
        return slice(mapped, pageable);
    }

    @Override
    public Page<MarketplaceProduct> searchProducts(ProductSearchCriteria criteria, Pageable pageable) {
        List<MarketplaceProductIndex> all = productIndexRepository.findAll().stream()
                .filter(MarketplaceProductIndex::isPublished)
                .filter(p -> matchesProduct(criteria, p))
                .toList();
        List<MarketplaceProduct> mapped = all.stream().map(mapper::toProduct).toList();
        return slice(mapped, pageable);
    }

    @Override
    public Optional<MarketplaceProduct> findByBarcode(String barcode) {
        if (barcode == null || barcode.isBlank()) {
            return Optional.empty();
        }
        return productIndexRepository.findFirstByBarcodeAndPublishedTrue(barcode).map(mapper::toProduct);
    }

    @Override
    public List<MarketplaceStore> findStoresNearProduct(UUID productId, GeoCriteria geo) {
        List<UUID> storeIds = inventoryIndexRepository.findByProductIdAndPublishedTrue(productId).stream()
                .map(MarketplaceInventoryIndex::getStoreId)
                .distinct()
                .toList();
        List<MarketplaceStore> stores = new ArrayList<>();
        for (UUID storeId : storeIds) {
            storeIndexRepository.findByStoreIdAndPublishedTrue(storeId).ifPresent(index -> {
                Double distance = geo.hasGeo()
                        ? distanceKm(geo.lat(), geo.lng(), index.getLatitude(), index.getLongitude())
                        : null;
                if (geo.hasGeo() && distance != null && distance > geo.radiusKm().doubleValue()) {
                    return;
                }
                stores.add(mapper.toStore(index, distance));
            });
        }
        stores.sort(Comparator.comparing(s -> s.distanceKm() != null ? s.distanceKm() : Double.MAX_VALUE));
        return stores;
    }

    private boolean isVisibleInSearch(MarketplaceStoreIndex store) {
        if (!properties.getMarketplace().getSearch().isPublicVisibilityRequired()) {
            return true;
        }
        return "PUBLIC".equals(store.getVisibility());
    }

    private static boolean matchesStore(StoreSearchCriteria c, MarketplaceStoreIndex s) {
        if (c.city() != null && !c.city().equalsIgnoreCase(s.getCity())) {
            return false;
        }
        if (c.pincode() != null && !c.pincode().equalsIgnoreCase(s.getPostalCode())) {
            return false;
        }
        if (c.q() != null && !contains(s.getSearchText(), c.q()) && !contains(s.getName(), c.q())) {
            return false;
        }
        return true;
    }

    private static boolean matchesProduct(ProductSearchCriteria c, MarketplaceProductIndex p) {
        if (c.storeId() != null && !c.storeId().equals(p.getStoreId())) {
            return false;
        }
        if (c.barcode() != null && !c.barcode().equalsIgnoreCase(p.getBarcode())) {
            return false;
        }
        if (c.sku() != null && !c.sku().equalsIgnoreCase(p.getSku())) {
            return false;
        }
        if (c.gtin() != null && !c.gtin().equalsIgnoreCase(p.getGtin())) {
            return false;
        }
        if (c.productCode() != null && !c.productCode().equalsIgnoreCase(p.getSku())) {
            return false;
        }
        if (c.brand() != null && !c.brand().equalsIgnoreCase(p.getBrand())) {
            return false;
        }
        if (c.categoryId() != null && !c.categoryId().equals(p.getCategoryId())) {
            return false;
        }
        if (c.q() != null && !matchesProductText(c.q(), p)) {
            return false;
        }
        return true;
    }

    private static boolean matchesProductText(String q, MarketplaceProductIndex p) {
        return contains(p.getSearchText(), q)
                || contains(p.getName(), q)
                || contains(p.getBrand(), q)
                || contains(p.getCategoryName(), q)
                || contains(p.getSku(), q)
                || contains(p.getBarcode(), q);
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase().contains(needle.toLowerCase());
    }

    private static Double distanceKm(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) {
            return null;
        }
        double r = 6371.0;
        double dLat = Math.toRadians(lat2.doubleValue() - lat1.doubleValue());
        double dLng = Math.toRadians(lng2.doubleValue() - lng1.doubleValue());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1.doubleValue()))
                        * Math.cos(Math.toRadians(lat2.doubleValue()))
                        * Math.sin(dLng / 2)
                        * Math.sin(dLng / 2);
        return r * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private static <T> Page<T> slice(List<T> all, Pageable pageable) {
        PageResponse<T> page = PageResponse.slice(all, pageable);
        return new PageImpl<>(page.content(), pageable, page.totalElements());
    }
}
