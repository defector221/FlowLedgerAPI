package com.flowledger.commerce.marketplace;

import com.flowledger.platform.event.bus.PlatformEventDispatcher;
import com.flowledger.platform.event.bus.PlatformEventEnvelope;
import com.flowledger.platform.event.bus.PlatformEventTypes;
import jakarta.annotation.PostConstruct;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CommerceInventorySyncConsumer {
    private final PlatformEventDispatcher dispatcher;
    private final CommerceMarketplaceInventorySyncService inventorySync;

    public CommerceInventorySyncConsumer(
            PlatformEventDispatcher dispatcher, CommerceMarketplaceInventorySyncService inventorySync) {
        this.dispatcher = dispatcher;
        this.inventorySync = inventorySync;
    }

    @PostConstruct
    void register() {
        dispatcher.register(PlatformEventTypes.ORDER_COMPLETED, this::onOrderCompleted);
    }

    void onOrderCompleted(PlatformEventEnvelope event) {
        UUID orderId = uuid(event.payload().get("orderId"));
        if (orderId == null) {
            return;
        }
        inventorySync.syncOrder(orderId, event.actorId());
    }

    private static UUID uuid(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof UUID id) {
            return id;
        }
        return UUID.fromString(value.toString());
    }
}
