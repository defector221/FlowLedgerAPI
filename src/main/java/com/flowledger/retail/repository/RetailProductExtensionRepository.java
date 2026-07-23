package com.flowledger.retail.repository;

import com.flowledger.retail.entity.RetailProductExtension;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailProductExtensionRepository extends JpaRepository<RetailProductExtension, UUID> {
    Optional<RetailProductExtension> findByOrganizationIdAndProductId(UUID organizationId, UUID productId);
}
