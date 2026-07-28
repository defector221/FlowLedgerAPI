package com.flowledger.commerce.publisher.repository;

import com.flowledger.commerce.publisher.entity.MarketplaceBrandIndex;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MarketplaceBrandIndexRepository extends JpaRepository<MarketplaceBrandIndex, UUID> {
    Optional<MarketplaceBrandIndex> findByOrganizationIdAndBrandName(UUID organizationId, String brandName);

    @Query("SELECT b FROM MarketplaceBrandIndex b WHERE b.published = true ORDER BY b.brandName")
    List<MarketplaceBrandIndex> findAllPublished();

    List<MarketplaceBrandIndex> findByOrganizationIdAndPublishedTrue(UUID organizationId);
}
