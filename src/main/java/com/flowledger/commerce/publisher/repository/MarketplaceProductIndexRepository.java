package com.flowledger.commerce.publisher.repository;

import com.flowledger.commerce.publisher.entity.MarketplaceProductIndex;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MarketplaceProductIndexRepository extends JpaRepository<MarketplaceProductIndex, UUID> {
    Optional<MarketplaceProductIndex> findByStoreIdAndProductId(UUID storeId, UUID productId);

    List<MarketplaceProductIndex> findByStoreId(UUID storeId);

    Optional<MarketplaceProductIndex> findFirstByBarcodeAndPublishedTrue(String barcode);

    List<MarketplaceProductIndex> findByBarcodeAndPublishedTrue(String barcode);

    Optional<MarketplaceProductIndex> findByIdAndPublishedTrue(UUID id);

    @Modifying
    @Query("UPDATE MarketplaceProductIndex i SET i.published = false WHERE i.storeId = :storeId")
    void unpublishAllByStoreId(UUID storeId);
}
