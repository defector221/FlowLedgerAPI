package com.flowledger.platform.event.bus;

import com.flowledger.commerce.events.CartCreatedEvent;
import com.flowledger.commerce.events.CommerceCatalogChangeEvent;
import com.flowledger.commerce.events.CommerceOrderPlacedEvent;
import com.flowledger.commerce.events.CustomerRegisteredEvent;
import com.flowledger.commerce.events.StorePublishedEvent;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class CommercePlatformEventBridge {
    private final PlatformEventBus bus;

    public CommercePlatformEventBridge(PlatformEventBus bus) {
        this.bus = bus;
    }

    @EventListener
    public void onOrderPlaced(CommerceOrderPlacedEvent event) {
        bus.publish(
                PlatformEventTypes.ORDER_CREATED,
                event.getOrganizationId(),
                "CommerceOrder",
                event.getEntityId(),
                Map.of("orderId", event.getEntityId(), "customerId", event.getActorId()),
                event.getActorId(),
                event.getCorrelationId());
    }

    @EventListener
    public void onCustomerRegistered(CustomerRegisteredEvent event) {
        bus.publish(
                PlatformEventTypes.CUSTOMER_REGISTERED,
                event.getOrganizationId(),
                "CommerceCustomer",
                event.getEntityId(),
                Map.of("customerId", event.getEntityId()),
                event.getEntityId(),
                event.getCorrelationId());
    }

    @EventListener
    public void onStorePublished(StorePublishedEvent event) {
        bus.publish(
                PlatformEventTypes.STORE_PUBLISHED,
                event.getOrganizationId(),
                "Store",
                event.getEntityId(),
                Map.of("storeId", event.getEntityId()),
                event.getActorId(),
                event.getCorrelationId());
    }

    @EventListener
    public void onCatalogChange(CommerceCatalogChangeEvent event) {
        bus.publish(
                PlatformEventTypes.PRODUCT_UPDATED,
                event.getOrganizationId(),
                event.getEntityType(),
                event.getEntityId(),
                Map.of(
                        "entityType", event.getEntityType(),
                        "entityId", event.getEntityId(),
                        "storeId", event.storeId() != null ? event.storeId() : ""),
                event.getActorId(),
                event.getCorrelationId());
    }

    @EventListener
    public void onCartCreated(CartCreatedEvent event) {
        bus.publish(
                PlatformEventTypes.CART_CREATED,
                event.getOrganizationId(),
                "CommerceCart",
                event.getEntityId(),
                Map.of("cartId", event.getEntityId(), "customerId", event.getActorId() != null ? event.getActorId() : ""),
                event.getActorId(),
                event.getCorrelationId());
    }

    public void publishOrderCompleted(UUID organizationId, UUID orderId, UUID customerId) {
        bus.publish(
                PlatformEventTypes.ORDER_COMPLETED,
                organizationId,
                "CommerceOrder",
                orderId,
                Map.of("orderId", orderId, "customerId", customerId != null ? customerId : ""),
                customerId,
                UUID.randomUUID());
    }

    public void publishPaymentSucceeded(UUID organizationId, UUID checkoutSessionId, UUID customerId, Object amount) {
        bus.publish(
                PlatformEventTypes.PAYMENT_SUCCEEDED,
                organizationId,
                "CheckoutSession",
                checkoutSessionId,
                Map.of("checkoutSessionId", checkoutSessionId, "amount", amount != null ? amount : 0),
                customerId,
                UUID.randomUUID());
    }
}
