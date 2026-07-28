package com.flowledger.commerce.rules.engine;

import com.flowledger.commerce.rules.entity.CommercePromotionRule;
import com.flowledger.commerce.rules.repository.CommercePromotionRuleRepository;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CouponEngine {
    private final CommercePromotionRuleRepository rules;

    public CouponEngine(CommercePromotionRuleRepository rules) {
        this.rules = rules;
    }

    public Optional<CommercePromotionRule> validate(UUID organizationId, String couponCode) {
        if (couponCode == null || couponCode.isBlank()) return Optional.empty();
        return rules.findByOrganizationIdAndCouponCodeIgnoreCaseAndActiveTrue(organizationId, couponCode.trim())
                .filter(this::isRedeemable);
    }

    public boolean isRedeemable(CommercePromotionRule rule) {
        if (!rule.isActive()) return false;
        if (rule.getMaxRedemptions() != null && rule.getRedemptionCount() >= rule.getMaxRedemptions()) {
            return false;
        }
        return rule.getDiscountAmount() != null || rule.getDiscountPercent() != null;
    }

    public BigDecimal previewDiscount(CommercePromotionRule rule, BigDecimal billAmount) {
        if (rule.getDiscountPercent() != null && billAmount != null) {
            return billAmount.multiply(rule.getDiscountPercent()).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
        }
        return rule.getDiscountAmount() != null ? rule.getDiscountAmount() : BigDecimal.ZERO;
    }
}
