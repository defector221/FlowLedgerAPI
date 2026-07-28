package com.flowledger.commerce.loyalty;

import com.flowledger.commerce.order.entity.CommerceOrder;
import com.flowledger.commerce.order.entity.CommerceOrderLine;
import com.flowledger.commerce.order.repository.CommerceOrderLineRepository;
import com.flowledger.commerce.order.repository.CommerceOrderRepository;
import com.flowledger.commerce.rules.context.PromotionContext;
import com.flowledger.commerce.rules.context.PromotionLine;
import com.flowledger.commerce.rules.context.PromotionResult;
import com.flowledger.commerce.rules.context.RewardOutcome;
import com.flowledger.commerce.rules.domain.RewardOutcomeType;
import com.flowledger.commerce.rules.domain.SalesChannel;
import com.flowledger.commerce.rules.engine.PromotionEngine;
import com.flowledger.platform.event.bus.PlatformEventEnvelope;
import com.flowledger.platform.event.bus.PlatformEventDispatcher;
import com.flowledger.platform.event.bus.PlatformEventTypes;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class LoyaltyEarnConsumer {
    private final PlatformEventDispatcher dispatcher;
    private final PromotionEngine promotionEngine;
    private final CommerceLoyaltyBridgeService loyalty;
    private final CommerceOrderRepository orders;
    private final CommerceOrderLineRepository orderLines;

    public LoyaltyEarnConsumer(
            PlatformEventDispatcher dispatcher,
            PromotionEngine promotionEngine,
            CommerceLoyaltyBridgeService loyalty,
            CommerceOrderRepository orders,
            CommerceOrderLineRepository orderLines) {
        this.dispatcher = dispatcher;
        this.promotionEngine = promotionEngine;
        this.loyalty = loyalty;
        this.orders = orders;
        this.orderLines = orderLines;
    }

    @PostConstruct
    void register() {
        dispatcher.register(PlatformEventTypes.ORDER_COMPLETED, this::onOrderCompleted);
    }

    @Transactional
    void onOrderCompleted(PlatformEventEnvelope event) {
        UUID orderId = uuid(event.payload().get("orderId"));
        if (orderId == null) return;

        CommerceOrder order = orders.findById(orderId).orElse(null);
        if (order == null) return;

        List<CommerceOrderLine> lines = orderLines.findByOrderIdOrderByCreatedAtAsc(orderId);
        List<PromotionLine> promoLines = lines.stream()
                .map(l -> new PromotionLine(l.getProductId(), l.getVariantId(), null, null, l.getQuantity(), l.getLineSubtotal()))
                .toList();

        PromotionContext ctx = PromotionContext.forEarn(
                order.getOrganizationId(),
                order.getStoreId(),
                order.getCustomerId(),
                SalesChannel.COMMERCE,
                order.getGrandTotal(),
                orderId,
                promoLines);
        PromotionResult result = promotionEngine.applyEarnRules(ctx);

        for (RewardOutcome outcome : result.allOutcomes()) {
            if (outcome.type() == RewardOutcomeType.REWARD_POINTS) {
                loyalty.earn(
                        order.getOrganizationId(),
                        order.getCustomerId(),
                        outcome.amount(),
                        "OrderCompleted",
                        orderId);
            }
        }
    }

    private static UUID uuid(Object v) {
        if (v == null || v.toString().isBlank()) return null;
        return UUID.fromString(v.toString());
    }
}
