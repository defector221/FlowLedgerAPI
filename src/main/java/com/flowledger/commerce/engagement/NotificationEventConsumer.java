package com.flowledger.commerce.engagement;

import com.flowledger.platform.event.bus.PlatformEventDispatcher;
import com.flowledger.platform.event.bus.PlatformEventEnvelope;
import com.flowledger.platform.event.bus.PlatformEventTypes;
import jakarta.annotation.PostConstruct;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class NotificationEventConsumer {
    private final PlatformEventDispatcher dispatcher;
    private final CommerceCustomerNotificationService notifications;

    public NotificationEventConsumer(PlatformEventDispatcher dispatcher, CommerceCustomerNotificationService notifications) {
        this.dispatcher = dispatcher;
        this.notifications = notifications;
    }

    @PostConstruct
    void register() {
        dispatcher.register(PlatformEventTypes.ORDER_CREATED, this::onOrderCreated);
        dispatcher.register(PlatformEventTypes.ORDER_COMPLETED, this::onOrderCompleted);
        dispatcher.register(PlatformEventTypes.PAYMENT_SUCCEEDED, this::onPaymentSucceeded);
        dispatcher.register(PlatformEventTypes.REWARD_CREDITED, this::onRewardCredited);
    }

    @Transactional
    void onOrderCreated(PlatformEventEnvelope event) {
        UUID customerId = uuid(event.payload().get("customerId"));
        if (customerId == null) return;
        notifications.create(
                customerId,
                event.organizationId(),
                PlatformEventTypes.ORDER_CREATED,
                "Order confirmed",
                "Your order has been placed successfully.");
    }

    @Transactional
    void onOrderCompleted(PlatformEventEnvelope event) {
        UUID customerId = uuid(event.payload().get("customerId"));
        if (customerId == null) return;
        notifications.create(
                customerId,
                event.organizationId(),
                PlatformEventTypes.ORDER_COMPLETED,
                "Order complete",
                "Your order is ready or has been delivered.");
    }

    @Transactional
    void onPaymentSucceeded(PlatformEventEnvelope event) {
        UUID customerId = event.actorId();
        if (customerId == null) return;
        notifications.create(
                customerId,
                event.organizationId(),
                PlatformEventTypes.PAYMENT_SUCCEEDED,
                "Payment received",
                "We received your payment. Thank you!");
    }

    @Transactional
    void onRewardCredited(PlatformEventEnvelope event) {
        UUID customerId = uuid(event.payload().get("customerId"));
        if (customerId == null) return;
        Object amount = event.payload().get("amount");
        String type = string(event.payload().get("outcomeType"));
        notifications.create(
                customerId,
                event.organizationId(),
                PlatformEventTypes.REWARD_CREDITED,
                "Reward credited",
                "You earned " + amount + " " + (type != null ? type.toLowerCase().replace('_', ' ') : "reward") + ".");
    }

    private static UUID uuid(Object v) {
        if (v == null || v.toString().isBlank()) return null;
        return UUID.fromString(v.toString());
    }

    private static String string(Object v) {
        return v != null ? v.toString() : null;
    }
}
