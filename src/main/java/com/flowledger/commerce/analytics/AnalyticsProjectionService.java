package com.flowledger.commerce.analytics;

import com.flowledger.commerce.analytics.entity.AnalyticsFunnelEvent;
import com.flowledger.commerce.analytics.entity.AnalyticsOrderFact;
import com.flowledger.commerce.analytics.entity.AnalyticsPromotionFact;
import com.flowledger.commerce.analytics.repository.AnalyticsFunnelEventRepository;
import com.flowledger.commerce.analytics.repository.AnalyticsOrderFactRepository;
import com.flowledger.commerce.analytics.repository.AnalyticsPromotionFactRepository;
import com.flowledger.commerce.order.entity.CommerceOrder;
import com.flowledger.commerce.order.repository.CommerceOrderRepository;
import com.flowledger.platform.event.bus.PlatformEventDispatcher;
import com.flowledger.platform.event.bus.PlatformEventEnvelope;
import com.flowledger.platform.event.bus.PlatformEventTypes;
import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AnalyticsProjectionService {
    private final PlatformEventDispatcher dispatcher;
    private final AnalyticsOrderFactRepository orderFacts;
    private final AnalyticsPromotionFactRepository promotionFacts;
    private final AnalyticsFunnelEventRepository funnelEvents;
    private final CommerceOrderRepository orders;

    public AnalyticsProjectionService(
            PlatformEventDispatcher dispatcher,
            AnalyticsOrderFactRepository orderFacts,
            AnalyticsPromotionFactRepository promotionFacts,
            AnalyticsFunnelEventRepository funnelEvents,
            CommerceOrderRepository orders) {
        this.dispatcher = dispatcher;
        this.orderFacts = orderFacts;
        this.promotionFacts = promotionFacts;
        this.funnelEvents = funnelEvents;
        this.orders = orders;
    }

    @PostConstruct
    void register() {
        dispatcher.register(PlatformEventTypes.ORDER_CREATED, this::onOrderCreated);
        dispatcher.register(PlatformEventTypes.ORDER_COMPLETED, this::onOrderCompleted);
        dispatcher.register(PlatformEventTypes.PROMOTION_APPLIED, this::onPromotionApplied);
        dispatcher.register(PlatformEventTypes.CART_CREATED, this::onFunnelEvent);
    }

    @Transactional
    void onOrderCreated(PlatformEventEnvelope event) {
        UUID orderId = uuid(event.payload().get("orderId"));
        if (orderId == null) return;
        orders.findById(orderId).ifPresent(this::projectOrderPlaced);
        recordFunnel(event.organizationId(), uuid(event.payload().get("customerId")), PlatformEventTypes.ORDER_CREATED);
    }

    @Transactional
    void onOrderCompleted(PlatformEventEnvelope event) {
        UUID orderId = uuid(event.payload().get("orderId"));
        if (orderId == null) return;
        orderFacts.findByOrderId(orderId).ifPresent(fact -> {
            fact.setStatus("COMPLETED");
            fact.setCompletedAt(OffsetDateTime.now());
            orderFacts.save(fact);
        });
        recordFunnel(event.organizationId(), uuid(event.payload().get("customerId")), PlatformEventTypes.ORDER_COMPLETED);
    }

    @Transactional
    void onPromotionApplied(PlatformEventEnvelope event) {
        AnalyticsPromotionFact fact = new AnalyticsPromotionFact();
        fact.setOrganizationId(event.organizationId());
        fact.setRuleId(uuid(event.payload().get("ruleId")));
        fact.setOrderId(uuid(event.payload().get("orderId")));
        fact.setDiscountApplied(decimal(event.payload().get("discountApplied")));
        fact.setOutcomeType("DISCOUNT");
        promotionFacts.save(fact);
    }

    @Transactional
    void onFunnelEvent(PlatformEventEnvelope event) {
        recordFunnel(event.organizationId(), event.actorId(), event.eventType());
    }

    @Transactional(readOnly = true)
    public List<AnalyticsOrderFact> orderFacts(UUID organizationId, OffsetDateTime from, OffsetDateTime to) {
        return orderFacts.findByOrganizationIdAndPlacedAtBetweenOrderByPlacedAtDesc(organizationId, from, to);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> funnelCounts(UUID organizationId, OffsetDateTime from, OffsetDateTime to) {
        return Map.of(
                PlatformEventTypes.ORDER_CREATED,
                        funnelEvents.countByOrganizationIdAndEventTypeAndOccurredAtBetween(
                                organizationId, PlatformEventTypes.ORDER_CREATED, from, to),
                PlatformEventTypes.ORDER_COMPLETED,
                        funnelEvents.countByOrganizationIdAndEventTypeAndOccurredAtBetween(
                                organizationId, PlatformEventTypes.ORDER_COMPLETED, from, to));
    }

    private void projectOrderPlaced(CommerceOrder order) {
        if (orderFacts.findByOrderId(order.getId()).isPresent()) return;
        AnalyticsOrderFact fact = new AnalyticsOrderFact();
        fact.setOrganizationId(order.getOrganizationId());
        fact.setStoreId(order.getStoreId());
        fact.setOrderId(order.getId());
        fact.setCustomerId(order.getCustomerId());
        fact.setChannel("COMMERCE");
        fact.setOrderTotal(order.getGrandTotal());
        fact.setDiscountTotal(order.getDiscountTotal());
        fact.setStatus(order.getStatus().name());
        fact.setPlacedAt(order.getCreatedAt());
        orderFacts.save(fact);
    }

    private void recordFunnel(UUID organizationId, UUID customerId, String eventType) {
        AnalyticsFunnelEvent row = new AnalyticsFunnelEvent();
        row.setOrganizationId(organizationId);
        row.setCustomerId(customerId);
        row.setEventType(eventType);
        funnelEvents.save(row);
    }

    private static UUID uuid(Object v) {
        if (v == null || v.toString().isBlank()) return null;
        try {
            return UUID.fromString(v.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static BigDecimal decimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        return new BigDecimal(v.toString());
    }
}
