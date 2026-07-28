package com.flowledger.commerce.rules.engine;

import com.flowledger.commerce.rules.context.PromotionContext;
import com.flowledger.commerce.rules.context.PromotionMatch;
import com.flowledger.commerce.rules.context.RewardOutcome;
import com.flowledger.commerce.rules.domain.PromotionRuleType;
import com.flowledger.commerce.rules.domain.RewardOutcomeType;
import com.flowledger.commerce.rules.entity.CommercePromotionRule;
import com.flowledger.commerce.rules.entity.CommerceRewardOutcome;
import com.flowledger.commerce.rules.repository.CommerceRewardOutcomeRepository;
import com.flowledger.platform.event.bus.PlatformEventBus;
import com.flowledger.platform.event.bus.PlatformEventTypes;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class RewardEngine {
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final CommerceRewardOutcomeRepository outcomes;
    private final PlatformEventBus eventBus;

    public RewardEngine(CommerceRewardOutcomeRepository outcomes, PlatformEventBus eventBus) {
        this.outcomes = outcomes;
        this.eventBus = eventBus;
    }

    public List<RewardOutcome> dispatch(CommercePromotionRule rule, PromotionContext ctx) {
        List<RewardOutcome> result = new ArrayList<>();
        RewardOutcomeType type = rule.getRewardType();
        BigDecimal amount = computeAmount(rule, ctx, type);
        if (amount.signum() <= 0) return result;

        RewardOutcome outcome = new RewardOutcome(
                type,
                amount,
                "INR",
                rule.getId(),
                rule.getCode(),
                rule.getName());
        result.add(outcome);

        if (ctx.earnPhase() && ctx.customerId() != null) {
            persistOutcome(rule, ctx, type, amount);
            eventBus.publish(
                    PlatformEventTypes.REWARD_CREDITED,
                    ctx.organizationId(),
                    "RewardOutcome",
                    rule.getId(),
                    Map.of(
                            "customerId", ctx.customerId(),
                            "outcomeType", type.name(),
                            "amount", amount,
                            "ruleCode", rule.getCode()),
                    ctx.customerId(),
                    UUID.randomUUID());
        }
        return result;
    }

    public List<RewardOutcome> dispatchMatch(PromotionMatch match, PromotionContext ctx) {
        return match.outcomes();
    }

    private void persistOutcome(CommercePromotionRule rule, PromotionContext ctx, RewardOutcomeType type, BigDecimal amount) {
        CommerceRewardOutcome row = new CommerceRewardOutcome();
        row.setOrganizationId(ctx.organizationId());
        row.setCustomerId(ctx.customerId());
        row.setRuleId(rule.getId());
        row.setOutcomeType(type);
        row.setAmount(amount);
        row.setCurrency("INR");
        outcomes.save(row);
    }

    private BigDecimal computeAmount(CommercePromotionRule rule, PromotionContext ctx, RewardOutcomeType type) {
        BigDecimal base = nz(ctx.orderTotal());
        return switch (rule.getRuleType()) {
            case PERCENTAGE, CATEGORY, BRAND, SEGMENT, FIRST_ORDER -> {
                if (rule.getDiscountPercent() != null) {
                    yield base.multiply(rule.getDiscountPercent())
                            .divide(HUNDRED, 2, RoundingMode.HALF_UP);
                }
                yield nz(rule.getDiscountAmount());
            }
            case FLAT, COUPON -> nz(rule.getDiscountAmount());
            case CASHBACK_EARN -> {
                if (rule.getDiscountPercent() != null) {
                    yield base.multiply(rule.getDiscountPercent())
                            .divide(HUNDRED, 2, RoundingMode.HALF_UP);
                }
                yield nz(rule.getDiscountAmount());
            }
            case POINTS_EARN -> {
                if (rule.getDiscountPercent() != null) {
                    yield base.multiply(rule.getDiscountPercent()).setScale(0, RoundingMode.FLOOR);
                }
                yield nz(rule.getDiscountAmount());
            }
            case BOGO, BUNDLE -> nz(rule.getDiscountAmount());
        };
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
