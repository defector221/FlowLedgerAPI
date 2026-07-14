package com.flowledger.product.repository;

import com.flowledger.product.entity.Product;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    Optional<Product> findByIdAndOrganizationId(UUID id, UUID org);

    boolean existsByOrganizationIdAndSku(UUID org, String sku);

    List<Product> findByOrganizationId(UUID organizationId);

    Page<Product> findByOrganizationId(UUID organizationId, Pageable pageable);
}
