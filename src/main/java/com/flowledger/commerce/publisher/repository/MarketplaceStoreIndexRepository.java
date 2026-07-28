package com.flowledger.commerce.publisher.repository;

import com.flowledger.commerce.publisher.entity.MarketplaceStoreIndex;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketplaceStoreIndexRepository extends JpaRepository<MarketplaceStoreIndex, UUID> {
    Optional<MarketplaceStoreIndex> findByStoreId(UUID storeId);

    Optional<MarketplaceStoreIndex> findByStoreIdAndPublishedTrue(UUID storeId);

    List<MarketplaceStoreIndex> findByOrganizationIdAndPublishedTrue(UUID organizationId);
}
