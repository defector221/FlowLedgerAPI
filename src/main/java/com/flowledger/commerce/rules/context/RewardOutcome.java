package com.flowledger.commerce.rules.context;

import com.flowledger.commerce.rules.domain.RewardOutcomeType;
import java.math.BigDecimal;
import java.util.UUID;

public record RewardOutcome(
        RewardOutcomeType type,
        BigDecimal amount,
        String currency,
        UUID ruleId,
        String ruleCode,
        String description) {}
