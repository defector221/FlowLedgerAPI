package com.flowledger.commerce.rules.engine;

import com.flowledger.commerce.rules.context.PromotionContext;
import com.flowledger.commerce.rules.context.PromotionMatch;
import com.flowledger.commerce.rules.context.PromotionResult;
import com.flowledger.commerce.rules.context.RewardOutcome;
import com.flowledger.commerce.rules.domain.PromotionRuleType;
import com.flowledger.commerce.rules.domain.RewardOutcomeType;
import com.flowledger.commerce.rules.entity.CommercePromotionRedemption;
import com.flowledger.commerce.rules.entity.CommercePromotionRule;
import com.flowledger.commerce.rules.repository.CommercePromotionRedemptionRepository;
import com.flowledger.commerce.rules.repository.CommercePromotionRuleRepository;
import com.flowledger.platform.event.bus.PlatformEventBus;
import com.flowledger.platform.event.bus.PlatformEventTypes;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PromotionEngine {
    private final CommercePromotionRuleRepository rules;
    private final CommercePromotionRedemptionRepository redemptions;
    private final RuleEngine ruleEngine;
    private final RewardEngine rewardEngine;
    private final PlatformEventBus eventBus;

    public PromotionEngine(
            CommercePromotionRuleRepository rules,
            CommercePromotionRedemptionRepository redemptions,
            RuleEngine ruleEngine,
            RewardEngine rewardEngine,
            PlatformEventBus eventBus) {
        this.rules = rules;
        this.redemptions = redemptions;
        this.ruleEngine = ruleEngine;
        this.rewardEngine = rewardEngine;
        this.eventBus = eventBus;
    }

    public PromotionResult evaluate(PromotionContext ctx) {
        List<CommercePromotionRule> candidates = rules.findByOrganizationIdAndActiveTrueOrderByPriorityAsc(ctx.organizationId());
        List<PromotionMatch> matches = new ArrayList<>();
        BigDecimal discountTotal = BigDecimal.ZERO;

        for (CommercePromotionRule rule : candidates) {
            if (ctx.earnPhase()) continue;
            if (isEarnRule(rule)) continue;
            if (rule.getRuleType() == PromotionRuleType.COUPON) {
                if (ctx.couponCode() == null
                        || rule.getCouponCode() == null
                        || !rule.getCouponCode().equalsIgnoreCase(ctx.couponCode())) {
                    continue;
                }
            }
            if (!ruleEngine.matches(rule, ctx)) continue;

            List<RewardOutcome> outcomes = rewardEngine.dispatch(rule, ctx);
            List<RewardOutcome> discounts = outcomes.stream()
                    .filter(o -> o.type() == RewardOutcomeType.DISCOUNT)
                    .toList();
            if (!discounts.isEmpty()) {
                BigDecimal ruleDiscount = discounts.stream()
                        .map(RewardOutcome::amount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                discountTotal = discountTotal.add(ruleDiscount);
                matches.add(new PromotionMatch(rule.getId(), rule.getCode(), rule.getName(), discounts));
            }
        }

        List<RewardOutcome> allOutcomes = matches.stream()
                .flatMap(m -> m.outcomes().stream())
                .toList();
        return new PromotionResult(discountTotal, matches, allOutcomes, discountTotal.signum() > 0);
    }

    public PromotionResult applyEarnRules(PromotionContext ctx) {
        List<CommercePromotionRule> candidates = rules.findByOrganizationIdAndActiveTrueOrderByPriorityAsc(ctx.organizationId());
        List<PromotionMatch> matches = new ArrayList<>();

        for (CommercePromotionRule rule : candidates) {
            if (!isEarnRule(rule)) continue;
            if (!ruleEngine.matches(rule, ctx)) continue;

            List<RewardOutcome> outcomes = rewardEngine.dispatch(rule, ctx);
            if (!outcomes.isEmpty()) {
                matches.add(new PromotionMatch(rule.getId(), rule.getCode(), rule.getName(), outcomes));
            }
        }

        List<RewardOutcome> allOutcomes = matches.stream()
                .flatMap(m -> m.outcomes().stream())
                .toList();
        return new PromotionResult(BigDecimal.ZERO, matches, allOutcomes, !allOutcomes.isEmpty());
    }

    public void recordRedemption(
            UUID organizationId,
            UUID ruleId,
            UUID customerId,
            UUID orderId,
            String couponCode,
            BigDecimal discountApplied) {
        CommercePromotionRedemption redemption = new CommercePromotionRedemption();
        redemption.setOrganizationId(organizationId);
        redemption.setRuleId(ruleId);
        redemption.setCustomerId(customerId);
        redemption.setOrderId(orderId);
        redemption.setCouponCode(couponCode);
        redemption.setDiscountApplied(discountApplied);
        redemptions.save(redemption);

        rules.findById(ruleId).ifPresent(rule -> {
            rule.setRedemptionCount(rule.getRedemptionCount() + 1);
            rules.save(rule);
        });

        eventBus.publish(
                PlatformEventTypes.PROMOTION_APPLIED,
                organizationId,
                "PromotionRedemption",
                redemption.getId(),
                Map.of(
                        "ruleId", ruleId,
                        "orderId", orderId != null ? orderId : "",
                        "discountApplied", discountApplied),
                customerId,
                UUID.randomUUID());
    }

    private static boolean isEarnRule(CommercePromotionRule rule) {
        return rule.getRuleType() == PromotionRuleType.CASHBACK_EARN
                || rule.getRuleType() == PromotionRuleType.POINTS_EARN
                || rule.getRewardType() == RewardOutcomeType.CASHBACK
                || rule.getRewardType() == RewardOutcomeType.REWARD_POINTS;
    }
}
