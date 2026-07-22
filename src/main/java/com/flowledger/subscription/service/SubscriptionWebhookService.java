package com.flowledger.subscription.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.subscription.domain.BillingCycle;
import com.flowledger.subscription.entity.PaymentTransaction;
import com.flowledger.subscription.entity.PaymentWebhookEvent;
import com.flowledger.subscription.entity.SubscriptionPlan;
import com.flowledger.subscription.integration.PaymentProvider;
import com.flowledger.subscription.integration.PaymentProviderRegistry;
import com.flowledger.subscription.repository.PaymentTransactionRepository;
import com.flowledger.subscription.repository.PaymentWebhookEventRepository;
import com.flowledger.subscription.repository.SubscriptionPlanRepository;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional
public class SubscriptionWebhookService {
    private final PaymentWebhookEventRepository webhookEvents;
    private final PaymentTransactionRepository paymentTransactions;
    private final SubscriptionPlanRepository plans;
    private final SubscriptionActivationService activationService;
    private final PaymentProviderRegistry paymentProviders;
    private final ObjectMapper objectMapper;

    public SubscriptionWebhookService(
            PaymentWebhookEventRepository webhookEvents,
            PaymentTransactionRepository paymentTransactions,
            SubscriptionPlanRepository plans,
            SubscriptionActivationService activationService,
            PaymentProviderRegistry paymentProviders,
            ObjectMapper objectMapper) {
        this.webhookEvents = webhookEvents;
        this.paymentTransactions = paymentTransactions;
        this.plans = plans;
        this.activationService = activationService;
        this.paymentProviders = paymentProviders;
        this.objectMapper = objectMapper;
    }

    /** Backward-compatible Razorpay entry point. */
    public void handleRazorpay(String payload, String signature) {
        handleProvider("razorpay", payload, signature);
    }

    public void handleProvider(String providerName, String payload, String signature) {
        PaymentProvider provider = paymentProviders.require(providerName);
        boolean signatureValid = provider.verifyWebhookSignature(payload, signature);

        JsonNode root;
        try {
            root = objectMapper.readTree(payload == null ? "{}" : payload);
        } catch (Exception e) {
            throw new BusinessException("Invalid webhook payload");
        }

        String eventId = resolveEventId(providerName, root);
        String eventType = resolveEventType(providerName, root);

        Optional<PaymentWebhookEvent> existing = webhookEvents.findByEventId(eventId);
        if (existing.isPresent() && existing.get().isProcessed()) {
            log.info("Ignoring duplicate {} webhook event {}", providerName, eventId);
            return;
        }

        PaymentWebhookEvent event = existing.orElseGet(PaymentWebhookEvent::new);
        event.setProvider(providerName);
        event.setEventId(eventId);
        event.setEventType(eventType);
        event.setPayload(root);
        event.setSignatureValid(signatureValid);
        event = webhookEvents.save(event);

        if (!signatureValid) {
            throw new BusinessException("Invalid webhook signature");
        }

        if (isPaidEvent(providerName, eventType, root)) {
            processPaidEvent(providerName, root);
        }

        event.setProcessed(true);
        event.setProcessedAt(OffsetDateTime.now());
        webhookEvents.save(event);
    }

    private void processPaidEvent(String providerName, JsonNode root) {
        String orderId = resolveOrderId(providerName, root);
        String paymentId = resolvePaymentId(providerName, root);
        if (orderId == null || orderId.isBlank()) {
            log.warn("{} webhook missing order id", providerName);
            return;
        }

        PaymentTransaction txn =
                paymentTransactions.findByProviderOrderId(orderId).orElse(null);
        if (txn == null) {
            log.warn("No payment transaction for {} order {}", providerName, orderId);
            return;
        }
        if ("PAID".equals(txn.getStatus())) {
            return;
        }

        SubscriptionPlan plan =
                plans.findById(txn.getPlanId()).orElseThrow(() -> new BusinessException("Subscription plan not found"));
        BillingCycle cycle;
        try {
            cycle = BillingCycle.valueOf(txn.getBillingCycle().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            cycle = BillingCycle.MONTHLY;
        }
        activationService.activate(txn.getOrganizationId(), plan, cycle, txn, providerName, paymentId);
    }

    private static boolean isPaidEvent(String provider, String eventType, JsonNode root) {
        String type = eventType == null ? "" : eventType.toLowerCase(Locale.ROOT);
        return switch (provider.toLowerCase(Locale.ROOT)) {
            case "razorpay" -> type.contains("payment.captured") || type.contains("order.paid");
            case "stripe" -> type.contains("payment_intent.succeeded") || type.contains("checkout.session.completed");
            case "cashfree" ->
                type.contains("payment_success")
                        || type.contains("order_paid")
                        || "PAID".equalsIgnoreCase(text(root, "order_status"))
                        || "SUCCESS".equalsIgnoreCase(text(root.path("data").path("payment"), "payment_status"));
            case "paypal" ->
                type.contains("payment.capture.completed")
                        || type.contains("checkout.order.approved")
                        || type.contains("checkout.order.completed");
            default -> false;
        };
    }

    private static String resolveEventId(String provider, JsonNode root) {
        return switch (provider.toLowerCase(Locale.ROOT)) {
            case "razorpay" ->
                firstNonBlank(
                        text(root, "event_id"),
                        text(root.path("payload").path("payment").path("entity"), "id"),
                        text(root, "id"),
                        UUID.randomUUID().toString());
            case "stripe" -> firstNonBlank(text(root, "id"), UUID.randomUUID().toString());
            case "cashfree" ->
                firstNonBlank(
                        text(root, "event_id"),
                        text(root.path("data").path("order"), "order_id"),
                        text(root, "orderId"),
                        UUID.randomUUID().toString());
            case "paypal" -> firstNonBlank(text(root, "id"), UUID.randomUUID().toString());
            default -> UUID.randomUUID().toString();
        };
    }

    private static String resolveEventType(String provider, JsonNode root) {
        return switch (provider.toLowerCase(Locale.ROOT)) {
            case "cashfree" -> firstNonBlank(text(root, "type"), text(root, "event"), "unknown");
            default -> firstNonBlank(text(root, "event"), text(root, "type"), "unknown");
        };
    }

    private static String resolveOrderId(String provider, JsonNode root) {
        return switch (provider.toLowerCase(Locale.ROOT)) {
            case "razorpay" -> {
                JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
                if (paymentEntity.isMissingNode() || paymentEntity.isNull()) {
                    paymentEntity = root.path("payload").path("order").path("entity");
                }
                yield firstNonBlank(text(paymentEntity, "order_id"), text(paymentEntity, "id"));
            }
            case "stripe" ->
                firstNonBlank(
                        text(root.path("data").path("object"), "id"),
                        text(root.path("data").path("object"), "payment_intent"));
            case "cashfree" ->
                firstNonBlank(
                        text(root.path("data").path("order"), "order_id"),
                        text(root, "order_id"),
                        text(root, "orderId"));
            case "paypal" ->
                firstNonBlank(
                        text(root.path("resource").path("supplementary_data").path("related_ids"), "order_id"),
                        text(root.path("resource"), "id"),
                        text(
                                root.path("resource")
                                        .path("purchase_units")
                                        .path(0)
                                        .path("payments")
                                        .path("captures")
                                        .path(0),
                                "id"));
            default -> null;
        };
    }

    private static String resolvePaymentId(String provider, JsonNode root) {
        return switch (provider.toLowerCase(Locale.ROOT)) {
            case "razorpay" -> {
                JsonNode paymentEntity = root.path("payload").path("payment").path("entity");
                yield text(paymentEntity, "id");
            }
            case "stripe" ->
                firstNonBlank(
                        text(root.path("data").path("object"), "latest_charge"),
                        text(root.path("data").path("object"), "id"));
            case "cashfree" ->
                firstNonBlank(
                        text(root.path("data").path("payment"), "cf_payment_id"),
                        text(root.path("data").path("payment"), "payment_id"));
            case "paypal" ->
                firstNonBlank(
                        text(root.path("resource"), "id"),
                        text(
                                root.path("resource")
                                        .path("purchase_units")
                                        .path(0)
                                        .path("payments")
                                        .path("captures")
                                        .path(0),
                                "id"));
            default -> null;
        };
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text == null || text.isBlank() ? null : text;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
