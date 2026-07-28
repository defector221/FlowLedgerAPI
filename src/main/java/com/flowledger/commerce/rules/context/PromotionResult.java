package com.flowledger.commerce.rules.context;

import java.math.BigDecimal;
import java.util.List;

public record PromotionResult(
        BigDecimal discountTotal,
        List<PromotionMatch> matches,
        List<RewardOutcome> allOutcomes,
        boolean applied) {

    public static PromotionResult empty() {
        return new PromotionResult(BigDecimal.ZERO, List.of(), List.of(), false);
    }
}
