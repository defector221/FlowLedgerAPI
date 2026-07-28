package com.flowledger.commerce.rules.context;

import java.util.List;
import java.util.UUID;

public record PromotionMatch(UUID ruleId, String ruleCode, String ruleName, List<RewardOutcome> outcomes) {}
