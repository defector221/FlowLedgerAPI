package com.flowledger.commerce.marketplace;

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
    private final MarketplaceStoreIndexRepository storeIndexRepository;
    private final MarketplaceProductIndexRepository productIndexRepository;
    private final MarketplaceCategoryIndexRepository categoryIndexRepository;
    private final MarketplaceBrandIndexRepository brandIndexRepository;
    private final MarketplaceIndexMapper mapper;

    public MarketplaceSearchService(
            CommerceProperties properties,
            PostgresMarketplaceSearchBackend postgresBackend,
            ObjectProvider<OpenSearchMarketplaceSearchBackend> openSearchBackend,
            MarketplaceStoreIndexRepository storeIndexRepository,
            MarketplaceProductIndexRepository productIndexRepository,
            MarketplaceCategoryIndexRepository categoryIndexRepository,
            MarketplaceBrandIndexRepository brandIndexRepository,
            MarketplaceIndexMapper mapper) {
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
    }

    public PageResponse<MarketplaceStore> searchStores(StoreSearchCriteria criteria, Pageable pageable) {
        Page<MarketplaceStore> page = backend.searchStores(criteria, pageable);
        return PageResponse.from(page);
    }

    public MarketplaceStore getStore(UUID storeId) {
        MarketplaceStoreIndex index = storeIndexRepository
                .findByStoreIdAndPublishedTrue(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Store not found"));
        return mapper.toStore(index, null);
    }

    public PageResponse<MarketplaceProduct> searchProducts(ProductSearchCriteria criteria, Pageable pageable) {
        Page<MarketplaceProduct> page = backend.searchProducts(criteria, pageable);
        return PageResponse.from(page);
    }

    public MarketplaceProduct getProduct(UUID id) {
        MarketplaceProductIndex index = productIndexRepository
                .findByIdAndPublishedTrue(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found"));
        return mapper.toProduct(index);
    }

    public List<MarketplaceCategory> listCategories() {
        return categoryIndexRepository.findAllPublished().stream().map(mapper::toCategory).toList();
    }

    public List<MarketplaceBrand> listBrands() {
        return brandIndexRepository.findAllPublished().stream().map(mapper::toBrand).toList();
    }

    public Optional<MarketplaceProduct> findByBarcode(String barcode) {
        return backend.findByBarcode(barcode);
    }

    public List<MarketplaceStore> findStoresNearProduct(UUID productId, GeoCriteria geo) {
        return backend.findStoresNearProduct(productId, geo);
    }
}
