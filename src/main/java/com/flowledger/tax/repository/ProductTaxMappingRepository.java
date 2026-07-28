package com.flowledger.tax.repository;

import com.flowledger.tax.entity.ProductTaxMapping;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductTaxMappingRepository extends JpaRepository<ProductTaxMapping, UUID> {
    Optional<ProductTaxMapping> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<ProductTaxMapping> findByOrganizationIdAndProductId(UUID organizationId, UUID productId);
}
