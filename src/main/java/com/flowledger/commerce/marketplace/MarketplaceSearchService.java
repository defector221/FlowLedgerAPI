package com.flowledger.commerce.marketplace;

import com.flowledger.commerce.inventory.CommerceSellableInventoryService;
import com.flowledger.commerce.config.CommerceProperties;
import com.flowledger.commerce.marketplace.domain.MarketplaceBrand;
import com.flowledger.commerce.marketplace.domain.MarketplaceCategory;
import com.flowledger.commerce.marketplace.domain.MarketplaceProduct;
import com.flowledger.commerce.marketplace.domain.MarketplaceStore;
import com.flowledger.commerce.marketplace.mapper.MarketplaceIndexMapper;
import com.flowledger.commerce.marketplace.search.GeoCriteria;
import com.flowledger.commerce.marketplace.search.MarketplaceSearchBackend;
import com.flowledger.commerce.marketplace.search.OpenSearchMarketplaceSearchBackend;
import com.flowledger.commerce.marketplace.search.PostgresMarketplaceSearchBackend;
import com.flowledger.commerce.marketplace.search.ProductSearchCriteria;
import com.flowledger.commerce.marketplace.search.StoreSearchCriteria;
import com.flowledger.commerce.publisher.entity.MarketplaceBrandIndex;
import com.flowledger.commerce.publisher.entity.MarketplaceCategoryIndex;
import com.flowledger.commerce.publisher.entity.MarketplaceProductIndex;
import com.flowledger.commerce.publisher.entity.MarketplaceStoreIndex;
import com.flowledger.commerce.publisher.repository.MarketplaceBrandIndexRepository;
import com.flowledger.commerce.publisher.repository.MarketplaceCategoryIndexRepository;
import com.flowledger.commerce.publisher.repository.MarketplaceProductIndexRepository;
import com.flowledger.commerce.publisher.repository.MarketplaceStoreIndexRepository;
import com.flowledger.common.dto.PageResponse;
import com.flowledger.common.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MarketplaceSearchService {
    private final MarketplaceSearchBackend backend;
    private final CommerceProperties properties;
    private final MarketplaceStoreIndexRepository storeIndexRepository;
    private final MarketplaceProductIndexRepository productIndexRepository;
    private final MarketplaceCategoryIndexRepository categoryIndexRepository;
    private final MarketplaceBrandIndexRepository brandIndexRepository;
    private final MarketplaceIndexMapper mapper;
    private final CommerceSellableInventoryService sellableInventory;

    public MarketplaceSearchService(
            CommerceProperties properties,
            PostgresMarketplaceSearchBackend postgresBackend,
            ObjectProvider<OpenSearchMarketplaceSearchBackend> openSearchBackend,
            MarketplaceStoreIndexRepository storeIndexRepository,
            MarketplaceProductIndexRepository productIndexRepository,
            MarketplaceCategoryIndexRepository categoryIndexRepository,
            MarketplaceBrandIndexRepository brandIndexRepository,
            MarketplaceIndexMapper mapper,
            CommerceSellableInventoryService sellableInventory) {
        this.properties = properties;
        if ("opensearch".equalsIgnoreCase(properties.getMarketplace().getSearch().getBackend())
                && openSearchBackend.getIfAvailable() != null) {
            this.backend = openSearchBackend.getObject();
        } else {
            this.backend = postgresBackend;
        }
        this.storeIndexRepository = storeIndexRepository;
        this.productIndexRepository = productIndexRepository;
        this.categoryIndexRepository = categoryIndexRepository;
        this.brandIndexRepository = brandIndexRepository;
        this.mapper = mapper;
        this.sellableInventory = sellableInventory;
    }

    public PageResponse<MarketplaceStore> searchStores(StoreSearchCriteria criteria, Pageable pageable) {
        Page<MarketplaceStore> page = backend.searchStores(applySearchConfig(criteria), pageable);
        return PageResponse.from(page);
    }

    public MarketplaceStore getStore(UUID storeId) {
        MarketplaceStoreIndex index = storeIndexRepository
                .findByStoreIdAndPublishedTrue(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found"));
        return mapper.toStore(index, null);
    }

    public PageResponse<MarketplaceProduct> searchProducts(ProductSearchCriteria criteria, Pageable pageable) {
        Page<MarketplaceProduct> page = backend.searchProducts(applyProductSearchConfig(criteria), pageable);
        return PageResponse.from(page.map(this::overlaySellableInventory));
    }

    public MarketplaceProduct getProduct(UUID id) {
        MarketplaceProductIndex index = productIndexRepository
                .findByIdAndPublishedTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        return overlaySellableInventory(mapper.toProduct(index), index.getOrganizationId());
    }

    public Optional<MarketplaceProduct> getProductByStoreAndProduct(UUID storeId, UUID productId) {
        return productIndexRepository
                .findByStoreIdAndProductId(storeId, productId)
                .filter(MarketplaceProductIndex::isPublished)
                .map(index -> overlaySellableInventory(mapper.toProduct(index), index.getOrganizationId()));
    }

    public List<MarketplaceCategory> listCategories() {
        return categoryIndexRepository.findAllPublished().stream().map(mapper::toCategory).toList();
    }

    public List<MarketplaceBrand> listBrands() {
        return brandIndexRepository.findAllPublished().stream().map(mapper::toBrand).toList();
    }

    public Optional<MarketplaceProduct> findByBarcode(String barcode) {
        return backend.findByBarcode(barcode).map(this::overlaySellableInventory);
    }

    public List<MarketplaceStore> findStoresNearProduct(UUID productId, GeoCriteria geo) {
        return backend.findStoresNearProduct(productId, applyGeoConfig(geo));
    }

    private StoreSearchCriteria applySearchConfig(StoreSearchCriteria criteria) {
        CommerceProperties.Marketplace.Search search = properties.getMarketplace().getSearch();
        StoreSearchCriteria effective = criteria;
        if (!search.isGeoFilterEnabled()) {
            effective = new StoreSearchCriteria(
                    criteria.q(),
                    criteria.city(),
                    criteria.pincode(),
                    null,
                    null,
                    null,
                    criteria.supportsDelivery(),
                    criteria.supportsPickup(),
                    criteria.supportsClickCollect());
        }
        return effective;
    }

    private ProductSearchCriteria applyProductSearchConfig(ProductSearchCriteria criteria) {
        if (properties.getMarketplace().getSearch().isGeoFilterEnabled()) {
            return criteria;
        }
        return new ProductSearchCriteria(
                criteria.q(),
                criteria.barcode(),
                criteria.sku(),
                criteria.gtin(),
                criteria.productCode(),
                criteria.brand(),
                criteria.categoryId(),
                criteria.storeId(),
                null,
                null,
                null);
    }

    private GeoCriteria applyGeoConfig(GeoCriteria geo) {
        if (geo == null || properties.getMarketplace().getSearch().isGeoFilterEnabled()) {
            return geo;
        }
        return new GeoCriteria(null, null, null);
    }

    private MarketplaceProduct overlaySellableInventory(MarketplaceProduct product) {
        return productIndexRepository
                .findByStoreIdAndProductId(product.storeId(), product.productId())
                .map(index -> overlaySellableInventory(product, index.getOrganizationId()))
                .orElse(product);
    }

    private MarketplaceProduct overlaySellableInventory(MarketplaceProduct product, UUID organizationId) {
        BigDecimal sellable = sellableInventory.sellableQty(organizationId, product.storeId(), product.productId());
        if (sellable == null || sellable.compareTo(product.inventoryQty()) == 0) {
            return product;
        }
        return new MarketplaceProduct(
                product.id(),
                product.storeId(),
                product.productId(),
                product.sku(),
                product.barcode(),
                product.gtin(),
                product.name(),
                product.description(),
                product.brand(),
                product.categoryId(),
                product.categoryName(),
                product.price(),
                product.currency(),
                sellable,
                product.imageUrls(),
                product.extras());
    }
}
