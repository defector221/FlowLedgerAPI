package com.flowledger.product.service;

import com.flowledger.common.service.OrganizationScopedService;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.product.dto.ProductIdentificationDtos.ImageResponse;
import com.flowledger.product.dto.ProductIdentificationDtos.ReorderImagesRequest;
import com.flowledger.product.entity.ProductImage;
import com.flowledger.product.repository.ProductImageRepository;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.storage.StorageService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ProductImageService extends OrganizationScopedService {
    private final ProductRepository products;
    private final ProductImageRepository images;
    private final StorageService storage;

    public ProductImageService(
            ProductRepository products, ProductImageRepository images, StorageService storage) {
        this.products = products;
        this.images = images;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<ImageResponse> list(UUID productId) {
        requireProduct(productId);
        return images.findByOrganizationIdAndProductIdOrderBySortOrderAsc(orgId(), productId).stream()
                .map(this::toResponse)
                .toList();
    }

    public ImageResponse upload(UUID productId, MultipartFile file, boolean primary) {
        requireProduct(productId);
        if (file == null || file.isEmpty()) {
            throw badRequest("file is required");
        }
        String key = "products/" + orgId() + "/" + productId + "/" + UUID.randomUUID() + suffix(file);
        storage.store(key, file);
        if (primary) {
            clearPrimary(productId);
        }
        int sortOrder = images.findByOrganizationIdAndProductIdOrderBySortOrderAsc(orgId(), productId).size();
        ProductImage row = new ProductImage();
        row.setOrganizationId(orgId());
        row.setProductId(productId);
        row.setObjectKey(key);
        row.setSortOrder(sortOrder);
        row.setPrimary(primary || sortOrder == 0);
        row.setMimeType(file.getContentType());
        row.setSizeBytes(file.getSize());
        TenantContext.userId().ifPresent(user -> {
            row.setCreatedBy(user);
            row.setUpdatedBy(user);
        });
        return toResponse(images.save(row));
    }

    public void delete(UUID productId, UUID imageId) {
        ProductImage row = images.findByIdAndOrganizationIdAndProductId(imageId, orgId(), productId)
                .orElseThrow(() -> notFound("Image not found"));
        storage.delete(row.getObjectKey());
        if (row.getThumbnailKey() != null) {
            storage.delete(row.getThumbnailKey());
        }
        images.delete(row);
    }

    public List<ImageResponse> reorder(UUID productId, ReorderImagesRequest request) {
        requireProduct(productId);
        List<ProductImage> existing =
                images.findByOrganizationIdAndProductIdOrderBySortOrderAsc(orgId(), productId);
        List<UUID> order = request.imageIds() == null ? List.of() : request.imageIds();
        List<ProductImage> updated = new ArrayList<>();
        for (int i = 0; i < order.size(); i++) {
            UUID id = order.get(i);
            ProductImage row = existing.stream()
                    .filter(img -> img.getId().equals(id))
                    .findFirst()
                    .orElseThrow(() -> notFound("Image not found"));
            row.setSortOrder(i);
            TenantContext.userId().ifPresent(row::setUpdatedBy);
            updated.add(images.save(row));
        }
        return updated.stream().map(this::toResponse).toList();
    }

    private void clearPrimary(UUID productId) {
        for (ProductImage row : images.findByOrganizationIdAndProductIdOrderBySortOrderAsc(orgId(), productId)) {
            if (row.isPrimary()) {
                row.setPrimary(false);
                TenantContext.userId().ifPresent(row::setUpdatedBy);
                images.save(row);
            }
        }
    }

    private void requireProduct(UUID productId) {
        required(products.findByIdAndOrganizationId(productId, orgId()), "Product");
    }

    private String suffix(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name != null && name.contains(".")) {
            return name.substring(name.lastIndexOf('.'));
        }
        return "";
    }

    private ImageResponse toResponse(ProductImage row) {
        return new ImageResponse(
                row.getId(),
                row.getProductId(),
                row.getObjectKey(),
                row.getThumbnailKey(),
                row.getSortOrder(),
                row.isPrimary(),
                row.getMimeType(),
                row.getSizeBytes(),
                presign(row.getObjectKey()),
                presign(row.getThumbnailKey()),
                row.getCreatedAt());
    }

    private String presign(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        return storage.getPresignedUrl(key, Duration.ofHours(1));
    }

    private ResponseStatusException notFound(String message) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, message);
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
