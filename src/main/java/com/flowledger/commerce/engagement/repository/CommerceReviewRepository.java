package com.flowledger.commerce.engagement.repository;

import com.flowledger.commerce.engagement.entity.CommerceReview;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommerceReviewRepository extends JpaRepository<CommerceReview, UUID> {
    List<CommerceReview> findByOrganizationIdAndStoreIdOrderByCreatedAtDesc(UUID organizationId, UUID storeId);

    List<CommerceReview> findByOrganizationIdAndProductIdOrderByCreatedAtDesc(UUID organizationId, UUID productId);
}
