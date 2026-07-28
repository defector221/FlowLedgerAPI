package com.flowledger.tax.repository;

import com.flowledger.tax.entity.ProductCategoryTaxMapping;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductCategoryTaxMappingRepository extends JpaRepository<ProductCategoryTaxMapping, UUID> {
    Optional<ProductCategoryTaxMapping> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Optional<ProductCategoryTaxMapping> findByOrganizationIdAndProductCategoryId(
            UUID organizationId, UUID productCategoryId);
}
