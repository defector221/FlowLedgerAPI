package com.flowledger.commerce.publisher.repository;

import com.flowledger.commerce.publisher.entity.MarketplaceInventoryIndex;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MarketplaceInventoryIndexRepository extends JpaRepository<MarketplaceInventoryIndex, UUID> {
    Optional<MarketplaceInventoryIndex> findByStoreIdAndProductId(UUID storeId, UUID productId);

    List<MarketplaceInventoryIndex> findByProductIdAndPublishedTrue(UUID productId);

    @Modifying
    @Query("UPDATE MarketplaceInventoryIndex i SET i.published = false WHERE i.storeId = :storeId")
    void unpublishAllByStoreId(UUID storeId);
}
