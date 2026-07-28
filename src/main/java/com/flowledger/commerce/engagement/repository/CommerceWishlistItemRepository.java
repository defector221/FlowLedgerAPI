package com.flowledger.commerce.engagement.repository;

import com.flowledger.commerce.engagement.entity.CommerceWishlistItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommerceWishlistItemRepository extends JpaRepository<CommerceWishlistItem, UUID> {
    List<CommerceWishlistItem> findByCustomerIdAndOrganizationIdOrderByCreatedAtDesc(UUID customerId, UUID organizationId);

    void deleteByCustomerIdAndStoreIdAndProductId(UUID customerId, UUID storeId, UUID productId);
}
