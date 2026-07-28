package com.flowledger.commerce.marketplace;

import com.flowledger.commerce.config.CommerceProperties;
import com.flowledger.product.entity.ProductImage;
import com.flowledger.product.repository.ProductImageRepository;
import com.flowledger.storage.StorageService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class MarketplaceImagePublisher {
    private final ProductImageRepository images;
    private final StorageService storage;
    private final CommerceProperties properties;

    public MarketplaceImagePublisher(
            ProductImageRepository images, StorageService storage, CommerceProperties properties) {
        this.images = images;
        this.storage = storage;
        this.properties = properties;
    }

    public List<String> publishProductImages(UUID organizationId, UUID productId) {
        List<ProductImage> rows = images.findByOrganizationIdAndProductIdOrderBySortOrderAsc(organizationId, productId);
        if (rows.isEmpty()) {
            rows = images.findByProductIdOrderBySortOrderAsc(productId);
        }
        Duration ttl = Duration.ofMinutes(properties.getMarketplace().getImageUrlTtlMinutes());
        List<String> urls = new ArrayList<>();
        for (ProductImage row : rows) {
            String key = row.getThumbnailKey() != null && !row.getThumbnailKey().isBlank()
                    ? row.getThumbnailKey()
                    : row.getObjectKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            urls.add(storage.getPresignedUrl(key, ttl));
        }
        return urls;
    }
}
