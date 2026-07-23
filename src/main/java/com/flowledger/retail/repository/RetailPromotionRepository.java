package com.flowledger.retail.repository;

import com.flowledger.retail.entity.RetailPromotion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetailPromotionRepository extends JpaRepository<RetailPromotion, UUID> {
    List<RetailPromotion> findByOrganizationIdAndDeletedFalseOrderByNameAsc(UUID organizationId);

    Optional<RetailPromotion> findByIdAndOrganizationIdAndDeletedFalse(UUID id, UUID organizationId);

    Optional<RetailPromotion> findFirstByOrganizationIdAndCouponCodeIgnoreCaseAndActiveTrueAndDeletedFalse(
            UUID organizationId, String couponCode);

    boolean existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(UUID organizationId, String code);
}
