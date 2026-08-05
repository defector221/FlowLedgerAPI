package com.flowledger.commerce.publisher;

import com.flowledger.commerce.integration.entity.MerchantIntegrationProfile;
import com.flowledger.commerce.marketplace.MarketplaceImagePublisher;
import com.flowledger.commerce.merchant.domain.MerchantType;
import com.flowledger.commerce.store.entity.StoreCommerceProfile;
import com.flowledger.inventory.repository.InventoryCostLayerRepository;
import com.flowledger.product.entity.Category;
import com.flowledger.product.entity.Product;
import com.flowledger.product.repository.CategoryRepository;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.retail.entity.RetailStore;
import com.flowledger.retail.repository.RetailStoreRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class FlowLedgerCatalogAdapter implements CatalogAdapter {
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final MarketplaceImagePublisher imagePublisher;
    private final RetailStoreRepository stores;
    private final InventoryCostLayerRepository costLayers;

    public FlowLedgerCatalogAdapter(
            ProductRepository products,
            CategoryRepository categories,
            MarketplaceImagePublisher imagePublisher,
            RetailStoreRepository stores,
            InventoryCostLayerRepository costLayers) {
        this.products = products;
        this.categories = categories;
        this.imagePublisher = imagePublisher;
        this.stores = stores;
        this.costLayers = costLayers;
    }

    @Override
    public MerchantType merchantType() {
        return MerchantType.FLOWLEDGER;
    }

    @Override
    public List<CatalogItemSnapshot> loadCatalog(MerchantIntegrationProfile integration, StoreCommerceProfile profile) {
        UUID orgId = profile.getOrganizationId();
        Set<UUID> allowedCategories = profile.getAllowedCategoryIds() == null
                ? Set.of()
                : new HashSet<>(profile.getAllowedCategoryIds());
        AssortmentStock stock = resolveStockedProducts(profile);

        return products.findByOrganizationIdAndActiveTrue(orgId).stream()
                .filter(p -> allowedCategories.isEmpty()
                        || (p.getCategoryId() != null && allowedCategories.contains(p.getCategoryId())))
                .filter(p -> !stock.enforce() || stock.productIds().contains(p.getId()))
                .map(p -> toSnapshot(p, orgId))
                .toList();
    }

    @Override
    public Optional<CatalogItemSnapshot> loadItem(
            MerchantIntegrationProfile integration, StoreCommerceProfile profile, UUID productId) {
        AssortmentStock stock = resolveStockedProducts(profile);
        return products.findById(productId)
                .filter(p -> p.getOrganizationId().equals(profile.getOrganizationId()) && p.isActive())
                .filter(p -> {
                    List<UUID> allowed = profile.getAllowedCategoryIds();
                    return allowed == null
                            || allowed.isEmpty()
                            || (p.getCategoryId() != null && allowed.contains(p.getCategoryId()));
                })
                .filter(p -> !stock.enforce() || stock.productIds().contains(p.getId()))
                .map(p -> toSnapshot(p, profile.getOrganizationId()));
    }

    private AssortmentStock resolveStockedProducts(StoreCommerceProfile profile) {
        Optional<RetailStore> store = stores.findById(profile.getStoreId());
        if (store.isEmpty() || store.get().getWarehouseId() == null) {
            return new AssortmentStock(false, Set.of());
        }
        List<UUID> ids = costLayers.findProductIdsWithStock(profile.getOrganizationId(), store.get().getWarehouseId());
        if (ids == null || ids.isEmpty()) {
            // No cost-layer stock rows yet — keep catalog publishable; rely on category allow-list.
            return new AssortmentStock(false, Set.of());
        }
        return new AssortmentStock(true, new HashSet<>(ids));
    }

    private record AssortmentStock(boolean enforce, Set<UUID> productIds) {}

    private CatalogItemSnapshot toSnapshot(Product product, UUID orgId) {
        String categoryName = null;
        if (product.getCategoryId() != null) {
            categoryName = categories.findById(product.getCategoryId()).map(Category::getName).orElse(null);
        }
        String barcode = product.getBarcode();
        String gtin = isGtin(barcode) ? barcode : null;
        List<String> imageUrls = imagePublisher.publishProductImages(orgId, product.getId());
        return new CatalogItemSnapshot(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getSellingPrice(),
                product.getOpeningStock(),
                barcode,
                gtin,
                product.getBrand(),
                product.getCategoryId(),
                categoryName,
                "INR",
                imageUrls,
                System.currentTimeMillis());
    }

    private static boolean isGtin(String barcode) {
        return barcode != null && barcode.matches("\\d{8,14}");
    }
}
