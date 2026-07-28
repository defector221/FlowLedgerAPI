package com.flowledger.commerce.publisher.sink;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.commerce.marketplace.util.MarketplaceContentHash;
import com.flowledger.commerce.publisher.entity.MarketplaceBrandIndex;
import com.flowledger.commerce.publisher.entity.MarketplaceCategoryIndex;
import com.flowledger.commerce.publisher.entity.MarketplaceInventoryIndex;
import com.flowledger.commerce.publisher.entity.MarketplaceProductIndex;
import com.flowledger.commerce.publisher.entity.MarketplaceStoreIndex;
import com.flowledger.commerce.publisher.model.BrandPublishedSnapshot;
import com.flowledger.commerce.publisher.model.CategoryPublishedSnapshot;
import com.flowledger.commerce.publisher.model.ProductPublishedSnapshot;
import com.flowledger.commerce.publisher.model.StorePublishedSnapshot;
import com.flowledger.commerce.publisher.repository.MarketplaceBrandIndexRepository;
import com.flowledger.commerce.publisher.repository.MarketplaceCategoryIndexRepository;
import com.flowledger.commerce.publisher.repository.MarketplaceInventoryIndexRepository;
import com.flowledger.commerce.publisher.repository.MarketplaceProductIndexRepository;
import com.flowledger.commerce.publisher.repository.MarketplaceStoreIndexRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Writes normalized commerce publish snapshots to PostgreSQL marketplace read-model tables. */
@Component
@Order(100)
public class MarketplaceIndexPublishSink implements CommercePublishSink {
    private final MarketplaceStoreIndexRepository storeIndexRepository;
    private final MarketplaceProductIndexRepository productIndexRepository;
    private final MarketplaceInventoryIndexRepository inventoryIndexRepository;
    private final MarketplaceCategoryIndexRepository categoryIndexRepository;
    private final MarketplaceBrandIndexRepository brandIndexRepository;
    private final ObjectMapper objectMapper;

    public MarketplaceIndexPublishSink(
            MarketplaceStoreIndexRepository storeIndexRepository,
            MarketplaceProductIndexRepository productIndexRepository,
            MarketplaceInventoryIndexRepository inventoryIndexRepository,
            MarketplaceCategoryIndexRepository categoryIndexRepository,
            MarketplaceBrandIndexRepository brandIndexRepository,
            ObjectMapper objectMapper) {
        this.storeIndexRepository = storeIndexRepository;
        this.productIndexRepository = productIndexRepository;
        this.inventoryIndexRepository = inventoryIndexRepository;
        this.categoryIndexRepository = categoryIndexRepository;
        this.brandIndexRepository = brandIndexRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void onStorePublished(StorePublishedSnapshot snapshot) {
        MarketplaceStoreIndex index = storeIndexRepository
                .findByStoreId(snapshot.storeId())
                .orElseGet(MarketplaceStoreIndex::new);
        index.setOrganizationId(snapshot.organizationId());
        index.setStoreId(snapshot.storeId());
        index.setPublished(true);
        index.setVisibility(snapshot.visibility());
        index.setName(snapshot.name());
        index.setCity(snapshot.city());
        index.setPostalCode(snapshot.postalCode());
        index.setState(snapshot.state());
        index.setCountry(snapshot.country());
        index.setLatitude(snapshot.latitude());
        index.setLongitude(snapshot.longitude());
        index.setDiscoveryRadiusKm(snapshot.discoveryRadiusKm());
        index.setSearchText(joinSearch(snapshot.name(), snapshot.city(), snapshot.postalCode()));
        writePayload(index::setPayload, snapshot.payload());
        storeIndexRepository.save(index);
    }

    @Override
    @Transactional
    public void onStoreUnpublished(UUID organizationId, UUID storeId) {
        storeIndexRepository.findByStoreId(storeId).ifPresent(index -> {
            index.setPublished(false);
            storeIndexRepository.save(index);
        });
        productIndexRepository.unpublishAllByStoreId(storeId);
        inventoryIndexRepository.unpublishAllByStoreId(storeId);
    }

    @Override
    @Transactional
    public void onProductPublished(ProductPublishedSnapshot snapshot) {
        MarketplaceProductIndex index = productIndexRepository
                .findByStoreIdAndProductId(snapshot.storeId(), snapshot.productId())
                .orElseGet(MarketplaceProductIndex::new);
        index.setOrganizationId(snapshot.organizationId());
        index.setStoreId(snapshot.storeId());
        index.setProductId(snapshot.productId());
        index.setPublished(true);
        index.setSku(snapshot.sku());
        index.setBarcode(snapshot.barcode());
        index.setGtin(snapshot.gtin());
        index.setName(snapshot.name());
        index.setBrand(snapshot.brand());
        index.setCategoryId(snapshot.categoryId());
        index.setCategoryName(snapshot.categoryName());
        index.setPrice(snapshot.price());
        index.setCurrency(snapshot.currency());
        index.setInventoryQty(snapshot.inventoryQty());
        index.setSearchText(joinSearch(
                snapshot.name(), snapshot.sku(), snapshot.barcode(), snapshot.brand(), snapshot.categoryName()));
        index.setVersion(snapshot.version());
        index.setContentHash(snapshot.contentHash());
        writeJson(index::setImageUrls, snapshot.imageUrls());
        writePayload(index::setPayload, snapshot.payload());
        productIndexRepository.save(index);

        if (snapshot.inventoryQty() != null) {
            MarketplaceInventoryIndex inv = inventoryIndexRepository
                    .findByStoreIdAndProductId(snapshot.storeId(), snapshot.productId())
                    .orElseGet(MarketplaceInventoryIndex::new);
            inv.setOrganizationId(snapshot.organizationId());
            inv.setStoreId(snapshot.storeId());
            inv.setProductId(snapshot.productId());
            inv.setInventoryQty(snapshot.inventoryQty());
            inv.setPublished(true);
            inv.setVersion(snapshot.version());
            inv.setContentHash(MarketplaceContentHash.hash(objectMapper, Map.of("qty", snapshot.inventoryQty())));
            inventoryIndexRepository.save(inv);
        }
    }

    @Override
    @Transactional
    public void onProductUnpublished(UUID organizationId, UUID storeId, UUID productId) {
        productIndexRepository.findByStoreIdAndProductId(storeId, productId).ifPresent(p -> {
            p.setPublished(false);
            productIndexRepository.save(p);
        });
        inventoryIndexRepository.findByStoreIdAndProductId(storeId, productId).ifPresent(inv -> {
            inv.setPublished(false);
            inventoryIndexRepository.save(inv);
        });
    }

    @Override
    @Transactional
    public void onCategoryPublished(CategoryPublishedSnapshot snapshot) {
        MarketplaceCategoryIndex index = categoryIndexRepository
                .findByOrganizationIdAndCategoryId(snapshot.organizationId(), snapshot.categoryId())
                .orElseGet(MarketplaceCategoryIndex::new);
        index.setOrganizationId(snapshot.organizationId());
        index.setCategoryId(snapshot.categoryId());
        index.setName(snapshot.name());
        index.setParentId(snapshot.parentId());
        index.setPublished(true);
        index.setProductCount(snapshot.productCount());
        categoryIndexRepository.save(index);
    }

    @Override
    @Transactional
    public void onBrandPublished(BrandPublishedSnapshot snapshot) {
        MarketplaceBrandIndex index = brandIndexRepository
                .findByOrganizationIdAndBrandName(snapshot.organizationId(), snapshot.brandName())
                .orElseGet(MarketplaceBrandIndex::new);
        index.setOrganizationId(snapshot.organizationId());
        index.setBrandName(snapshot.brandName());
        index.setPublished(true);
        index.setProductCount(snapshot.productCount());
        brandIndexRepository.save(index);
    }

    @Transactional
    public void persistInventoryOnly(
            UUID organizationId, UUID storeId, UUID productId, BigDecimal qty, long version) {
        MarketplaceInventoryIndex inv = inventoryIndexRepository
                .findByStoreIdAndProductId(storeId, productId)
                .orElseGet(MarketplaceInventoryIndex::new);
        inv.setOrganizationId(organizationId);
        inv.setStoreId(storeId);
        inv.setProductId(productId);
        inv.setInventoryQty(qty);
        inv.setPublished(true);
        inv.setVersion(version);
        inv.setContentHash(MarketplaceContentHash.hash(objectMapper, Map.of("qty", qty)));
        inventoryIndexRepository.save(inv);
        productIndexRepository.findByStoreIdAndProductId(storeId, productId).ifPresent(p -> {
            p.setInventoryQty(qty);
            productIndexRepository.save(p);
        });
    }

    public List<MarketplaceProductIndex> findPublishedProductsByStore(UUID storeId) {
        return productIndexRepository.findByStoreId(storeId).stream()
                .filter(MarketplaceProductIndex::isPublished)
                .toList();
    }

    private void writePayload(java.util.function.Consumer<String> setter, Map<String, Object> payload) {
        try {
            setter.accept(objectMapper.writeValueAsString(payload != null ? payload : Map.of()));
        } catch (Exception e) {
            setter.accept("{}");
        }
    }

    private void writeJson(java.util.function.Consumer<String> setter, List<String> value) {
        try {
            setter.accept(objectMapper.writeValueAsString(value != null ? value : List.of()));
        } catch (Exception e) {
            setter.accept("[]");
        }
    }

    private static String joinSearch(String... parts) {
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
