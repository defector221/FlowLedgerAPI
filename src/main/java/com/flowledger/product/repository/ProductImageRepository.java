package com.flowledger.product.repository;

import com.flowledger.product.entity.ProductImage;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {
    List<ProductImage> findByOrganizationIdAndProductIdOrderBySortOrderAsc(UUID organizationId, UUID productId);

    Optional<ProductImage> findByIdAndOrganizationIdAndProductId(UUID id, UUID organizationId, UUID productId);

    Optional<ProductImage> findFirstByProductIdAndPrimaryTrueOrderBySortOrderAsc(UUID productId);

    List<ProductImage> findByProductIdOrderBySortOrderAsc(UUID productId);
}
