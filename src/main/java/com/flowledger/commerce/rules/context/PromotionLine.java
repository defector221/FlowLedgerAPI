package com.flowledger.commerce.rules.context;

import java.math.BigDecimal;
import java.util.UUID;

public record PromotionLine(
        UUID productId,
        UUID variantId,
        UUID categoryId,
        String brand,
        BigDecimal quantity,
        BigDecimal lineSubtotal) {}
