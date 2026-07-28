package com.flowledger.commerce.marketplace.search;

import com.flowledger.commerce.marketplace.domain.MarketplaceProduct;
import com.flowledger.commerce.marketplace.domain.MarketplaceStore;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MarketplaceSearchBackend {
    Page<MarketplaceStore> searchStores(StoreSearchCriteria criteria, Pageable pageable);

    Page<MarketplaceProduct> searchProducts(ProductSearchCriteria criteria, Pageable pageable);

    Optional<MarketplaceProduct> findByBarcode(String barcode);

    List<MarketplaceStore> findStoresNearProduct(java.util.UUID productId, GeoCriteria geo);
}
