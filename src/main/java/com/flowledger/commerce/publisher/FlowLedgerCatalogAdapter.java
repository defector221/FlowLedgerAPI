package com.flowledger.commerce.publisher;

import com.flowledger.commerce.integration.entity.MerchantIntegrationProfile;
import com.flowledger.commerce.marketplace.MarketplaceImagePublisher;
import com.flowledger.commerce.merchant.domain.MerchantType;
import com.flowledger.commerce.store.entity.StoreCommerceProfile;
import com.flowledger.product.entity.Category;
import com.flowledger.product.entity.Product;
import com.flowledger.product.repository.CategoryRepository;
import com.flowledger.product.repository.ProductRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class FlowLedgerCatalogAdapter implements CatalogAdapter {
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final MarketplaceImagePublisher imagePublisher;

    public FlowLedgerCatalogAdapter(
            ProductRepository products, CategoryRepository categories, MarketplaceImagePublisher imagePublisher) {
        this.products = products;
        this.categories = categories;
        this.imagePublisher = imagePublisher;
    }

    @Override
    public MerchantType merchantType() {
        return MerchantType.FLOWLEDGER;
    }

    @Override
    public List<CatalogItemSnapshot> loadCatalog(MerchantIntegrationProfile integration, StoreCommerceProfile profile) {
        UUID orgId = profile.getOrganizationId();
        return products.findByOrganizationIdAndActiveTrue(orgId).stream()
                .map(p -> toSnapshot(p, orgId))
                .toList();
    }

    @Override
    public Optional<CatalogItemSnapshot> loadItem(
            MerchantIntegrationProfile integration, StoreCommerceProfile profile, UUID productId) {
        return products.findById(productId)
                .filter(p -> p.getOrganizationId().equals(profile.getOrganizationId()) && p.isActive())
                .map(p -> toSnapshot(p, profile.getOrganizationId()));
    }

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
