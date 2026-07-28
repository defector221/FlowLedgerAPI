package com.flowledger.commerce.rules.adapter;

import com.flowledger.commerce.rules.context.PromotionContext;
import com.flowledger.commerce.rules.context.PromotionResult;
import com.flowledger.commerce.rules.domain.SalesChannel;
import com.flowledger.commerce.rules.engine.PromotionEngine;
import com.flowledger.common.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PosPromotionAdapter {
    private final PromotionEngine promotionEngine;

    public PosPromotionAdapter(PromotionEngine promotionEngine) {
        this.promotionEngine = promotionEngine;
    }

    public PromotionResult evaluatePosSale(UUID storeId, UUID customerId, BigDecimal billTotal, String couponCode) {
        UUID orgId = TenantContext.getOrganizationId();
        PromotionContext ctx = PromotionContext.forCheckout(
                orgId, storeId, customerId, SalesChannel.POS, billTotal, couponCode, false, List.of());
        return promotionEngine.evaluate(ctx);
    }
}
