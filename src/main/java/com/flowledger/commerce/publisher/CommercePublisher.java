package com.flowledger.commerce.publisher;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.commerce.events.ProductPublishedEvent;
import com.flowledger.commerce.events.ProductUnpublishedEvent;
import com.flowledger.commerce.events.StorePublishedEvent;
import com.flowledger.commerce.events.StoreUnpublishedEvent;
import com.flowledger.commerce.integration.entity.MerchantIntegrationProfile;
import com.flowledger.commerce.integration.repository.MerchantIntegrationProfileRepository;
import com.flowledger.commerce.marketplace.util.MarketplaceContentHash;
import com.flowledger.commerce.onboarding.entity.MerchantOnboarding;
import com.flowledger.commerce.onboarding.repository.MerchantOnboardingRepository;
import com.flowledger.commerce.publisher.entity.MarketplaceInventoryIndex;
import com.flowledger.commerce.publisher.entity.MarketplaceProductIndex;
import com.flowledger.commerce.publisher.model.BrandPublishedSnapshot;
import com.flowledger.commerce.publisher.model.CategoryPublishedSnapshot;
import com.flowledger.commerce.publisher.model.ProductPublishedSnapshot;
import com.flowledger.commerce.publisher.model.StorePublishedSnapshot;
import com.flowledger.commerce.publisher.repository.MarketplaceInventoryIndexRepository;
import com.flowledger.commerce.publisher.repository.MarketplaceProductIndexRepository;
import com.flowledger.commerce.publisher.sink.CommercePublishSink;
import com.flowledger.commerce.publisher.sink.MarketplaceIndexPublishSink;
import com.flowledger.commerce.store.entity.StoreCommerceProfile;
import com.flowledger.commerce.store.repository.StoreCommerceProfileRepository;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.platform.event.DomainEventPublisher;
import com.flowledger.retail.entity.RetailStore;
import com.flowledger.retail.repository.RetailStoreRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates ERP-to-commerce projection: builds normalized publish snapshots and fans them out
 * to every {@link CommercePublishSink} (marketplace PG index, OpenSearch, future feeds).
 */
@Service
@Transactional
public class CommercePublisher {
    private final MerchantIntegrationProfileRepository integrationProfiles;
    private final MerchantOnboardingRepository onboardingRepository;
    private final MarketplaceProductIndexRepository productIndexRepository;
    private final MarketplaceInventoryIndexRepository inventoryIndexRepository;
    private final MarketplaceIndexPublishSink indexSink;
    private final List<CommercePublishSink> sinks;
    private final StoreCommerceProfileRepository storeProfiles;
    private final RetailStoreRepository retailStores;
    private final List<CatalogAdapter> adapters;
    private final ObjectMapper objectMapper;
    private final DomainEventPublisher events;

    public CommercePublisher(
            MerchantIntegrationProfileRepository integrationProfiles,
            MerchantOnboardingRepository onboardingRepository,
            MarketplaceProductIndexRepository productIndexRepository,
            MarketplaceInventoryIndexRepository inventoryIndexRepository,
            MarketplaceIndexPublishSink indexSink,
            List<CommercePublishSink> sinks,
            StoreCommerceProfileRepository storeProfiles,
            RetailStoreRepository retailStores,
            List<CatalogAdapter> adapters,
            ObjectMapper objectMapper,
            DomainEventPublisher events) {
        this.integrationProfiles = integrationProfiles;
        this.onboardingRepository = onboardingRepository;
        this.productIndexRepository = productIndexRepository;
        this.inventoryIndexRepository = inventoryIndexRepository;
        this.indexSink = indexSink;
        this.sinks = sinks;
        this.storeProfiles = storeProfiles;
        this.retailStores = retailStores;
        this.adapters = adapters;
        this.objectMapper = objectMapper;
        this.events = events;
    }

    public int publishStore(StoreCommerceProfile profile, UUID actorId) {
        UUID orgId = profile.getOrganizationId();
        MerchantOnboarding onboarding = onboardingRepository
                .findByOrganizationId(orgId)
                .orElseThrow(() -> new BusinessException("Merchant onboarding not found"));
        if (!onboarding.isLive()) {
            throw new BusinessException("Merchant must be LIVE to publish stores");
        }
        if (!profile.isPublishedToMarketplace()) {
            throw new BusinessException("Store is not marked for marketplace publication");
        }

        MerchantIntegrationProfile integration = integrationProfiles
                .findByOrganizationId(orgId)
                .orElseThrow(() -> new BusinessException("Integration profile not found"));

        fanOutStore(buildStoreSnapshot(profile));
        int productsPublished = publishCatalog(profile, integration);
        publishCategories(profile, integration);
        publishBrands(profile, integration);
        events.publish(new StorePublishedEvent(this, orgId, actorId, profile.getStoreId()));
        return productsPublished;
    }

    public void unpublishStore(StoreCommerceProfile profile, UUID actorId) {
        UUID storeId = profile.getStoreId();
        UUID orgId = profile.getOrganizationId();
        profile.unpublishStoreFromMarketplace();

        List<MarketplaceProductIndex> products = productIndexRepository.findByStoreId(storeId).stream()
                .filter(MarketplaceProductIndex::isPublished)
                .toList();

        for (CommercePublishSink sink : sinks) {
            sink.onStoreUnpublished(orgId, storeId);
        }

        for (MarketplaceProductIndex product : products) {
            events.publish(new ProductUnpublishedEvent(this, orgId, actorId, storeId, product.getProductId()));
        }
        events.publish(new StoreUnpublishedEvent(this, orgId, actorId, storeId));
    }

    public void publishCatalogItem(UUID organizationId, UUID productId, UUID actorId) {
        MerchantIntegrationProfile integration = integrationProfiles
                .findByOrganizationId(organizationId)
                .orElse(null);
        if (integration == null) {
            return;
        }
        CatalogAdapter adapter = resolveAdapter(integration);
        if (adapter == null) {
            return;
        }
        for (StoreCommerceProfile profile : storeProfiles.findByOrganizationIdAndPublishedToMarketplaceTrue(organizationId)) {
            if (!profile.isPublishProducts() && !profile.isPublishPrices() && !profile.isPublishInventory()) {
                continue;
            }
            Optional<CatalogAdapter.CatalogItemSnapshot> item =
                    adapter.loadItem(integration, profile, productId);
            if (item.isEmpty()) {
                continue;
            }
            upsertCatalogItem(profile, item.get(), actorId);
        }
    }

    public void publishInventoryItem(UUID organizationId, UUID storeId, UUID productId, BigDecimal qty, UUID actorId) {
        StoreCommerceProfile profile = storeProfiles
                .findByStoreIdAndOrganizationId(storeId, organizationId)
                .orElse(null);
        if (profile == null || !profile.isPublishedToMarketplace() || !profile.isPublishInventory()) {
            return;
        }
        MarketplaceInventoryIndex index = inventoryIndexRepository
                .findByStoreIdAndProductId(storeId, productId)
                .orElseGet(MarketplaceInventoryIndex::new);
        String hash = MarketplaceContentHash.hash(objectMapper, Map.of("qty", qty));
        if (hash.equals(index.getContentHash()) && index.isPublished()) {
            return;
        }
        long version = index.getVersion() + 1;
        indexSink.persistInventoryOnly(organizationId, storeId, productId, qty, version);

        productIndexRepository.findByStoreIdAndProductId(storeId, productId).ifPresent(p -> {
            ProductPublishedSnapshot snapshot = snapshotFromIndex(p);
            fanOutProductDownstream(snapshot);
        });
    }

    private void fanOutStore(StorePublishedSnapshot snapshot) {
        for (CommercePublishSink sink : sinks) {
            sink.onStorePublished(snapshot);
        }
    }

    private void fanOutProduct(ProductPublishedSnapshot snapshot) {
        indexSink.onProductPublished(snapshot);
        fanOutProductDownstream(enrichWithIndexId(snapshot));
    }

    private void fanOutProductDownstream(ProductPublishedSnapshot snapshot) {
        for (CommercePublishSink sink : sinks) {
            if (sink != indexSink) {
                sink.onProductPublished(snapshot);
            }
        }
    }

    private ProductPublishedSnapshot enrichWithIndexId(ProductPublishedSnapshot snapshot) {
        UUID indexId = productIndexRepository
                .findByStoreIdAndProductId(snapshot.storeId(), snapshot.productId())
                .map(MarketplaceProductIndex::getId)
                .orElse(snapshot.indexId());
        if (indexId == null || indexId.equals(snapshot.indexId())) {
            return snapshot;
        }
        return new ProductPublishedSnapshot(
                indexId,
                snapshot.organizationId(),
                snapshot.storeId(),
                snapshot.productId(),
                snapshot.sku(),
                snapshot.barcode(),
                snapshot.gtin(),
                snapshot.name(),
                snapshot.description(),
                snapshot.brand(),
                snapshot.categoryId(),
                snapshot.categoryName(),
                snapshot.price(),
                snapshot.currency(),
                snapshot.inventoryQty(),
                snapshot.imageUrls(),
                snapshot.version(),
                snapshot.contentHash(),
                snapshot.payload());
    }

    private StorePublishedSnapshot buildStoreSnapshot(StoreCommerceProfile profile) {
        RetailStore store = retailStores
                .findById(profile.getStoreId())
                .orElseThrow(() -> new BusinessException("Retail store not found"));
        Map<String, Object> payload = new HashMap<>();
        payload.put("storeId", store.getId());
        payload.put("name", store.getName());
        payload.put("city", store.getCity());
        payload.put("supportsDelivery", profile.isSupportsDelivery());
        payload.put("supportsPickup", profile.isSupportsPickup());
        payload.put("supportsClickCollect", profile.isSupportsClickCollect());
        payload.put("supportsScanAndGo", profile.isSupportsScanAndGo());
        return new StorePublishedSnapshot(
                profile.getOrganizationId(),
                profile.getStoreId(),
                store.getName(),
                store.getCity(),
                store.getPostalCode(),
                store.getState(),
                store.getCountry() != null ? store.getCountry() : "IN",
                store.getLatitude(),
                store.getLongitude(),
                profile.getDiscoveryRadius(),
                profile.getVisibility().name(),
                profile.isSupportsDelivery(),
                profile.isSupportsPickup(),
                profile.isSupportsClickCollect(),
                profile.isSupportsScanAndGo(),
                payload);
    }

    private int publishCatalog(StoreCommerceProfile profile, MerchantIntegrationProfile integration) {
        if (!profile.isPublishProducts() && !profile.isPublishPrices() && !profile.isPublishInventory()) {
            return 0;
        }
        CatalogAdapter adapter = resolveAdapter(integration);
        if (adapter == null) {
            throw new BusinessException("No catalog adapter for merchant type");
        }
        List<CatalogAdapter.CatalogItemSnapshot> items = adapter.loadCatalog(integration, profile);
        int count = 0;
        for (CatalogAdapter.CatalogItemSnapshot item : items) {
            upsertCatalogItem(profile, item, null);
            count++;
        }
        return count;
    }

    private void publishCategories(StoreCommerceProfile profile, MerchantIntegrationProfile integration) {
        CatalogAdapter adapter = resolveAdapter(integration);
        if (adapter == null) {
            return;
        }
        Map<UUID, Integer> counts = new HashMap<>();
        for (CatalogAdapter.CatalogItemSnapshot item : adapter.loadCatalog(integration, profile)) {
            if (item.categoryId() != null) {
                counts.merge(item.categoryId(), 1, Integer::sum);
            }
        }
        Set<UUID> seen = new HashSet<>();
        for (CatalogAdapter.CatalogItemSnapshot item : adapter.loadCatalog(integration, profile)) {
            if (item.categoryId() == null || !seen.add(item.categoryId())) {
                continue;
            }
            CategoryPublishedSnapshot snapshot = new CategoryPublishedSnapshot(
                    profile.getOrganizationId(),
                    item.categoryId(),
                    item.categoryName() != null ? item.categoryName() : "Category",
                    null,
                    counts.getOrDefault(item.categoryId(), 0));
            for (CommercePublishSink sink : sinks) {
                sink.onCategoryPublished(snapshot);
            }
        }
    }

    private void publishBrands(StoreCommerceProfile profile, MerchantIntegrationProfile integration) {
        CatalogAdapter adapter = resolveAdapter(integration);
        if (adapter == null) {
            return;
        }
        Map<String, Integer> counts = new HashMap<>();
        for (CatalogAdapter.CatalogItemSnapshot item : adapter.loadCatalog(integration, profile)) {
            if (item.brand() != null && !item.brand().isBlank()) {
                counts.merge(item.brand(), 1, Integer::sum);
            }
        }
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            BrandPublishedSnapshot snapshot =
                    new BrandPublishedSnapshot(profile.getOrganizationId(), entry.getKey(), entry.getValue());
            for (CommercePublishSink sink : sinks) {
                sink.onBrandPublished(snapshot);
            }
        }
    }

    private void upsertCatalogItem(StoreCommerceProfile profile, CatalogAdapter.CatalogItemSnapshot item, UUID actorId) {
        if (profile.isPublishProducts()) {
            profile.assertCanPublishProducts();
        }
        Map<String, Object> hashPayload = buildProductHashPayload(profile, item);
        String hash = MarketplaceContentHash.hash(objectMapper, hashPayload);

        MarketplaceProductIndex existing = productIndexRepository
                .findByStoreIdAndProductId(profile.getStoreId(), item.productId())
                .orElse(null);
        if (existing != null && hash.equals(existing.getContentHash()) && existing.isPublished()) {
            return;
        }

        long version = existing != null ? existing.getVersion() + 1 : 1L;
        ProductPublishedSnapshot snapshot = buildProductSnapshot(profile, item, hash, version);
        fanOutProduct(snapshot);
        events.publish(new ProductPublishedEvent(
                this, profile.getOrganizationId(), actorId, profile.getStoreId(), item.productId()));
    }

    private ProductPublishedSnapshot buildProductSnapshot(
            StoreCommerceProfile profile, CatalogAdapter.CatalogItemSnapshot item, String hash, long version) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sku", item.sku());
        payload.put("name", item.name());
        if (profile.isPublishPrices()) {
            payload.put("price", item.price());
        }
        if (profile.isPublishInventory()) {
            payload.put("inventoryQty", item.inventoryQty());
        }
        if (profile.isPublishProducts()) {
            payload.put("description", item.description());
        }

        BigDecimal price = profile.isPublishPrices() ? item.price() : null;
        BigDecimal inventoryQty = profile.isPublishInventory() ? item.inventoryQty() : null;
        List<String> imageUrls =
                profile.isPublishProducts() && item.imageUrls() != null ? item.imageUrls() : List.of();

        return new ProductPublishedSnapshot(
                null,
                profile.getOrganizationId(),
                profile.getStoreId(),
                item.productId(),
                item.sku(),
                item.barcode(),
                item.gtin(),
                item.name(),
                item.description(),
                item.brand(),
                item.categoryId(),
                item.categoryName(),
                price,
                item.currency() != null ? item.currency() : "INR",
                inventoryQty,
                imageUrls,
                version,
                hash,
                payload);
    }

    private ProductPublishedSnapshot snapshotFromIndex(MarketplaceProductIndex index) {
        return new ProductPublishedSnapshot(
                index.getId(),
                index.getOrganizationId(),
                index.getStoreId(),
                index.getProductId(),
                index.getSku(),
                index.getBarcode(),
                index.getGtin(),
                index.getName(),
                null,
                index.getBrand(),
                index.getCategoryId(),
                index.getCategoryName(),
                index.getPrice(),
                index.getCurrency(),
                index.getInventoryQty(),
                List.of(),
                index.getVersion(),
                index.getContentHash(),
                Map.of());
    }

    private Map<String, Object> buildProductHashPayload(
            StoreCommerceProfile profile, CatalogAdapter.CatalogItemSnapshot item) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("sku", item.sku());
        payload.put("name", item.name());
        if (profile.isPublishPrices()) {
            payload.put("price", item.price());
        }
        if (profile.isPublishInventory()) {
            payload.put("inventoryQty", item.inventoryQty());
        }
        if (profile.isPublishProducts()) {
            payload.put("description", item.description());
            payload.put("images", item.imageUrls());
        }
        return payload;
    }

    private CatalogAdapter resolveAdapter(MerchantIntegrationProfile integration) {
        return adapters.stream()
                .filter(a -> a.merchantType() == integration.getMerchantType())
                .findFirst()
                .orElse(null);
    }
}
