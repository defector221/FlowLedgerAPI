package com.flowledger.commerce.rules.repository;

import com.flowledger.commerce.rules.entity.CommercePromotionRule;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercePromotionRuleRepository extends JpaRepository<CommercePromotionRule, UUID> {
    List<CommercePromotionRule> findByOrganizationIdAndActiveTrueOrderByPriorityAsc(UUID organizationId);

    Optional<CommercePromotionRule> findByOrganizationIdAndCodeAndActiveTrue(UUID organizationId, String code);

    Optional<CommercePromotionRule> findByOrganizationIdAndCouponCodeIgnoreCaseAndActiveTrue(
            UUID organizationId, String couponCode);
}
