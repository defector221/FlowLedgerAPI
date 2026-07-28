package com.flowledger.commerce.rules.context;

import com.flowledger.commerce.rules.domain.SalesChannel;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PromotionContext(
        UUID organizationId,
        UUID storeId,
        UUID customerId,
        SalesChannel channel,
        BigDecimal orderTotal,
        BigDecimal subtotal,
        String couponCode,
        boolean firstOrder,
        boolean earnPhase,
        BigDecimal walletRedeemAmount,
        BigDecimal pointsRedeemAmount,
        List<PromotionLine> lines,
        OffsetDateTime evaluatedAt) {

    public static PromotionContext forCheckout(
            UUID organizationId,
            UUID storeId,
            UUID customerId,
            SalesChannel channel,
            BigDecimal subtotal,
            String couponCode,
            boolean firstOrder,
            List<PromotionLine> lines) {
        return new PromotionContext(
                organizationId,
                storeId,
                customerId,
                channel,
                subtotal,
                subtotal,
                couponCode,
                firstOrder,
                false,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                lines != null ? lines : List.of(),
                OffsetDateTime.now());
    }

    public static PromotionContext forEarn(
            UUID organizationId,
            UUID storeId,
            UUID customerId,
            SalesChannel channel,
            BigDecimal orderTotal,
            UUID orderId,
            List<PromotionLine> lines) {
        return new PromotionContext(
                organizationId,
                storeId,
                customerId,
                channel,
                orderTotal,
                orderTotal,
                null,
                false,
                true,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                lines != null ? lines : List.of(),
                OffsetDateTime.now());
    }
}
