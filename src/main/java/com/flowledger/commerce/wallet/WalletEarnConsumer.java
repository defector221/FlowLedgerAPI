package com.flowledger.commerce.wallet;

import com.flowledger.commerce.rules.context.PromotionContext;
import com.flowledger.commerce.rules.context.PromotionLine;
import com.flowledger.commerce.rules.context.PromotionResult;
import com.flowledger.commerce.rules.context.RewardOutcome;
import com.flowledger.commerce.rules.domain.RewardOutcomeType;
import com.flowledger.commerce.rules.domain.SalesChannel;
import com.flowledger.commerce.rules.engine.PromotionEngine;
import com.flowledger.commerce.order.entity.CommerceOrder;
import com.flowledger.commerce.order.entity.CommerceOrderLine;
import com.flowledger.commerce.order.repository.CommerceOrderLineRepository;
import com.flowledger.commerce.order.repository.CommerceOrderRepository;
import com.flowledger.commerce.wallet.domain.WalletAccountType;
import com.flowledger.platform.event.bus.PlatformEventEnvelope;
import com.flowledger.platform.event.bus.PlatformEventDispatcher;
import com.flowledger.platform.event.bus.PlatformEventTypes;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class WalletEarnConsumer {
    private final PlatformEventDispatcher dispatcher;
    private final PromotionEngine promotionEngine;
    private final WalletLedgerService wallet;
    private final CommerceOrderRepository orders;
    private final CommerceOrderLineRepository orderLines;

    public WalletEarnConsumer(
            PlatformEventDispatcher dispatcher,
            PromotionEngine promotionEngine,
            WalletLedgerService wallet,
            CommerceOrderRepository orders,
            CommerceOrderLineRepository orderLines) {
        this.dispatcher = dispatcher;
        this.promotionEngine = promotionEngine;
        this.wallet = wallet;
        this.orders = orders;
        this.orderLines = orderLines;
    }

    @PostConstruct
    void register() {
        dispatcher.register(PlatformEventTypes.ORDER_COMPLETED, this::onOrderCompleted);
        dispatcher.register(PlatformEventTypes.REWARD_CREDITED, this::onRewardCredited);
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
            if (outcome.type() == RewardOutcomeType.CASHBACK) {
                wallet.credit(
                        order.getCustomerId(),
                        order.getOrganizationId(),
                        WalletAccountType.CASHBACK,
                        outcome.amount(),
                        "OrderCompleted",
                        orderId,
                        "Cashback from " + outcome.ruleCode());
            }
        }
    }

    @Transactional
    void onRewardCredited(PlatformEventEnvelope event) {
        String outcomeType = string(event.payload().get("outcomeType"));
        if (!RewardOutcomeType.CASHBACK.name().equals(outcomeType)) return;
        UUID customerId = uuid(event.payload().get("customerId"));
        if (customerId == null) return;
        BigDecimal amount = decimal(event.payload().get("amount"));
        if (amount.signum() <= 0) return;
        // Wallet credit handled in onOrderCompleted; this handler supports direct reward events
    }

    private static UUID uuid(Object v) {
        if (v == null || v.toString().isBlank()) return null;
        return UUID.fromString(v.toString());
    }

    private static String string(Object v) {
        return v != null ? v.toString() : null;
    }

    private static BigDecimal decimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        return new BigDecimal(v.toString());
    }
}
