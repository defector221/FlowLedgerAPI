package com.flowledger.commerce.rules.entity;

import com.flowledger.commerce.common.CommerceGlobalEntity;
import com.flowledger.commerce.rules.domain.PromotionRuleType;
import com.flowledger.commerce.rules.domain.RewardOutcomeType;
import com.flowledger.commerce.rules.domain.SalesChannel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "commerce_promotion_rules")
@Getter
@Setter
@NoArgsConstructor
public class CommercePromotionRule extends CommerceGlobalEntity {
    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "store_id")
    private UUID storeId;

    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false)
    private PromotionRuleType ruleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "reward_type", nullable = false)
    private RewardOutcomeType rewardType;

    @Column(name = "discount_percent")
    private BigDecimal discountPercent;

    @Column(name = "discount_amount")
    private BigDecimal discountAmount;

    @Column(name = "buy_qty")
    private BigDecimal buyQty;

    @Column(name = "get_qty")
    private BigDecimal getQty;

    @Column(name = "coupon_code")
    private String couponCode;

    @Column(name = "min_order_total")
    private BigDecimal minOrderTotal;

    @Column(name = "max_redemptions")
    private Integer maxRedemptions;

    @Column(name = "redemption_count", nullable = false)
    private int redemptionCount;

    @Column(nullable = false)
    private int priority = 100;

    @Enumerated(EnumType.STRING)
    @Column(name = "sales_channel")
    private SalesChannel salesChannel;

    @Column(name = "starts_at")
    private OffsetDateTime startsAt;

    @Column(name = "ends_at")
    private OffsetDateTime endsAt;

    @Column(nullable = false)
    private boolean active = true;
}
