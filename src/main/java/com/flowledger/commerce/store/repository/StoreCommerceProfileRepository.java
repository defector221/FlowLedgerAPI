package com.flowledger.commerce.store.repository;

import com.flowledger.commerce.store.entity.StoreCommerceProfile;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreCommerceProfileRepository extends JpaRepository<StoreCommerceProfile, UUID> {
    Optional<StoreCommerceProfile> findByStoreIdAndOrganizationId(UUID storeId, UUID organizationId);

    Optional<StoreCommerceProfile> findByStoreId(UUID storeId);

    List<StoreCommerceProfile> findByOrganizationId(UUID organizationId);

    long countByOrganizationIdAndCommerceEnabledTrue(UUID organizationId);

    List<StoreCommerceProfile> findByOrganizationIdAndPublishedToMarketplaceTrue(UUID organizationId);
}
