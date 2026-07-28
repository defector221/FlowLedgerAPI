package com.flowledger.commerce.pricing;

import java.math.BigDecimal;
import java.util.UUID;

public record CommerceLinePricing(
        UUID productId,
        UUID variantId,
        UUID warehouseId,
        BigDecimal unitPrice,
        String priceSource,
        String currency,
        BigDecimal lineSubtotal,
        BigDecimal discount,
        BigDecimal lineTax,
        BigDecimal lineTotal,
        BigDecimal taxRate,
        String taxType) {}
