package com.flowledger.commerce.ai;

import com.flowledger.platform.event.bus.PlatformEventDispatcher;
import com.flowledger.platform.event.bus.PlatformEventEnvelope;
import com.flowledger.platform.event.bus.PlatformEventTypes;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AiRecommendationConsumer {
    private static final Logger log = LoggerFactory.getLogger(AiRecommendationConsumer.class);

    private final PlatformEventDispatcher dispatcher;
    private final Map<UUID, Integer> productPurchaseCounts = new ConcurrentHashMap<>();

    public AiRecommendationConsumer(PlatformEventDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @PostConstruct
    void register() {
        dispatcher.register(PlatformEventTypes.ORDER_COMPLETED, this::onOrderCompleted);
    }

    void onOrderCompleted(PlatformEventEnvelope event) {
        UUID orderId = uuid(event.payload().get("orderId"));
        if (orderId == null) return;
        // Stub: accumulate order completion signals for future recommendation index
        log.debug("AI consumer recorded OrderCompleted for order {}", orderId);
    }

    private static UUID uuid(Object v) {
        if (v == null || v.toString().isBlank()) return null;
        return UUID.fromString(v.toString());
    }
}
