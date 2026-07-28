package com.flowledger.commerce.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.commerce.checkout.CheckoutService;
import com.flowledger.commerce.payment.domain.PaymentSessionStatus;
import com.flowledger.commerce.payment.entity.CommercePaymentSession;
import com.flowledger.commerce.payment.repository.CommercePaymentSessionRepository;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.subscription.integration.PaymentProvider;
import com.flowledger.subscription.integration.PaymentProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CommercePaymentWebhookService {
    private static final Logger log = LoggerFactory.getLogger(CommercePaymentWebhookService.class);

    private final CommercePaymentSessionRepository sessions;
    private final PaymentProviderRegistry paymentProviders;
    private final PaymentOrchestrator paymentOrchestrator;
    private final CheckoutService checkoutService;
    private final ObjectMapper objectMapper;

    public CommercePaymentWebhookService(
            CommercePaymentSessionRepository sessions,
            PaymentProviderRegistry paymentProviders,
            PaymentOrchestrator paymentOrchestrator,
            CheckoutService checkoutService,
            ObjectMapper objectMapper) {
        this.sessions = sessions;
        this.paymentProviders = paymentProviders;
        this.paymentOrchestrator = paymentOrchestrator;
        this.checkoutService = checkoutService;
        this.objectMapper = objectMapper;
    }

    public void handleProvider(String provider, String payload, String signature) {
        PaymentProvider gateway = paymentProviders.require(provider);
        if (signature != null && !gateway.verifyWebhookSignature(payload, signature)) {
            throw new BusinessException("Invalid webhook signature");
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            String event = root.path("event").asText("");
            if (!event.contains("payment") && !event.contains("order")) {
                log.debug("Ignoring commerce webhook event: {}", event);
                return;
            }

            JsonNode entity = root.path("payload").path("payment").has("entity")
                    ? root.path("payload").path("payment").path("entity")
                    : root.path("payload").path("payment");
            String orderId = entity.path("order_id").asText(null);
            String paymentId = entity.path("id").asText(null);
            if (orderId == null) {
                orderId = root.path("payload").path("order").path("entity").path("id").asText(null);
            }
            final String gatewayOrderId = orderId;
            if (gatewayOrderId == null) {
                log.warn("Commerce webhook missing order id");
                return;
            }

            CommercePaymentSession session = sessions.findByGatewayOrderId(gatewayOrderId)
                    .orElseThrow(() -> new BusinessException("Payment session not found for order " + gatewayOrderId));
            if (session.getStatus() == PaymentSessionStatus.PAID) {
                return;
            }

            paymentOrchestrator.markPaid(session, paymentId, signature, null);
            if (session.getStatus() == PaymentSessionStatus.PAID) {
                checkoutService.completePaidCheckout(session.getCheckoutSessionId(), session);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException("Failed to process commerce payment webhook: " + e.getMessage());
        }
    }
}
