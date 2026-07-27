package com.flowledger.product.repository;

import com.flowledger.product.entity.ProductImportJob;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductImportJobRepository extends JpaRepository<ProductImportJob, UUID> {
    Optional<ProductImportJob> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
