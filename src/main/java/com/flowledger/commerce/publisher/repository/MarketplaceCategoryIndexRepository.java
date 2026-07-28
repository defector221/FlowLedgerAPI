package com.flowledger.commerce.publisher.repository;

import com.flowledger.commerce.publisher.entity.MarketplaceCategoryIndex;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface MarketplaceCategoryIndexRepository extends JpaRepository<MarketplaceCategoryIndex, UUID> {
    Optional<MarketplaceCategoryIndex> findByOrganizationIdAndCategoryId(UUID organizationId, UUID categoryId);

    @Query("SELECT c FROM MarketplaceCategoryIndex c WHERE c.published = true ORDER BY c.name")
    List<MarketplaceCategoryIndex> findAllPublished();

    List<MarketplaceCategoryIndex> findByOrganizationIdAndPublishedTrue(UUID organizationId);
}
