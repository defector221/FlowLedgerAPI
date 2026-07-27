package com.flowledger.product.repository;

import com.flowledger.product.entity.ProductQrCode;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductQrCodeRepository extends JpaRepository<ProductQrCode, UUID> {
    Optional<ProductQrCode> findByOrganizationIdAndProductId(UUID organizationId, UUID productId);
}
