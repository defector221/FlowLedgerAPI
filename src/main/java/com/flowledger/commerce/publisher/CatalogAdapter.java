package com.flowledger.commerce.publisher;

import com.flowledger.commerce.integration.entity.MerchantIntegrationProfile;
import com.flowledger.commerce.merchant.domain.MerchantType;
import com.flowledger.commerce.store.entity.StoreCommerceProfile;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CatalogAdapter {
    MerchantType merchantType();

    List<CatalogItemSnapshot> loadCatalog(MerchantIntegrationProfile integration, StoreCommerceProfile profile);

    Optional<CatalogItemSnapshot> loadItem(
            MerchantIntegrationProfile integration, StoreCommerceProfile profile, UUID productId);

    record CatalogItemSnapshot(
            UUID productId,
            String sku,
            String name,
            String description,
            BigDecimal price,
            BigDecimal inventoryQty,
            String barcode,
            String gtin,
            String brand,
            UUID categoryId,
            String categoryName,
            String currency,
            List<String> imageUrls,
            long version) {
        public CatalogItemSnapshot(
                UUID productId,
                String sku,
                String name,
                String description,
                BigDecimal price,
                BigDecimal inventoryQty) {
            this(productId, sku, name, description, price, inventoryQty, null, null, null, null, null, "INR", List.of(), 0L);
        }
    }
}
