package com.flowledger.product.service;

import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.product.dto.PublicProductDtos.PublicProductCard;
import com.flowledger.product.entity.Product;
import com.flowledger.product.entity.ProductImage;
import com.flowledger.product.repository.ProductImageRepository;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.storage.StorageService;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class PublicProductService {
    private final ProductRepository products;
    private final ProductImageRepository images;
    private final OrganizationRepository organizations;
    private final StorageService storage;
    private final String frontendUrl;

    public PublicProductService(
            ProductRepository products,
            ProductImageRepository images,
            OrganizationRepository organizations,
            StorageService storage,
            @Value("${flowledger.app.frontend-url}") String frontendUrl) {
        this.products = products;
        this.images = images;
        this.organizations = organizations;
        this.storage = storage;
        this.frontendUrl = trimTrailingSlash(frontendUrl);
    }

    public PublicProductCard getPublishedProduct(UUID productId) {
        Product product = products
                .findById(productId)
                .filter(Product::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));

        String orgName = organizations.findById(product.getOrganizationId()).map(o -> o.getName()).orElse(null);
        String imageUrl = resolveImageUrl(product);

        return new PublicProductCard(
                product.getId(),
                product.getName(),
                product.getSku(),
                product.getBarcode(),
                product.getBrand(),
                product.getDescription(),
                product.getHsnSacCode(),
                orgName,
                imageUrl,
                publishedProductUrl(product.getId()));
    }

    public String publishedProductUrl(UUID productId) {
        return frontendUrl + "/p/" + productId;
    }

    private String resolveImageUrl(Product product) {
        return images.findFirstByProductIdAndPrimaryTrueOrderBySortOrderAsc(product.getId())
                .or(() -> images.findByProductIdOrderBySortOrderAsc(product.getId()).stream().findFirst())
                .map(ProductImage::getObjectKey)
                .or(() -> java.util.Optional.ofNullable(product.getImageObjectKey()).filter(k -> !k.isBlank()))
                .map(key -> storage.getPresignedUrl(key, Duration.ofHours(1)))
                .orElse(null);
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
