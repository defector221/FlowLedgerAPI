package com.flowledger.subscription.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.subscription.config.CashfreeProperties;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class CashfreeProvider implements PaymentProvider {
    private static final String API_VERSION = "2023-08-01";

    private final CashfreeProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    public CashfreeProvider(CashfreeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClientBuilder = RestClient.builder();
    }

    @Override
    public String name() {
        return "cashfree";
    }

    @Override
    public CreateOrderResult createOrder(CreateOrderRequest request) {
        if (!properties.isConfigured()) {
            String mockOrderId = "order_dev_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
            log.warn("Cashfree keys blank — returning mock order {}", mockOrderId);
            return new CreateOrderResult(mockOrderId, request.amount(), request.currency(), "{\"mock\":true}");
        }

        String orderId = request.receipt() == null
                ? "cf_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16)
                : request.receipt().replaceAll("[^A-Za-z0-9_-]", "_");
        if (orderId.length() > 45) {
            orderId = orderId.substring(0, 45);
        }

        Map<String, Object> body = Map.of(
                "order_id", orderId,
                "order_amount", request.amount(),
                "order_currency", request.currency() == null ? "INR" : request.currency(),
                "order_note", request.notes() == null ? "" : request.notes(),
                "customer_details", Map.of(
                        "customer_id", "cust_" + orderId,
                        "customer_phone", "9999999999"));

        String response = restClientBuilder
                .baseUrl(properties.apiBaseUrl())
                .build()
                .post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-client-id", properties.getAppId())
                .header("x-client-secret", properties.getSecretKey())
                .header("x-api-version", API_VERSION)
                .body(body)
                .retrieve()
                .body(String.class);

        try {
            JsonNode node = objectMapper.readTree(response);
            String cfOrderId = firstNonBlank(text(node, "order_id"), orderId);
            BigDecimal amount = node.has("order_amount")
                    ? new BigDecimal(node.path("order_amount").asText("0"))
                    : request.amount();
            String currency = node.path("order_currency").asText(request.currency() == null ? "INR" : request.currency());
            return new CreateOrderResult(cfOrderId, amount, currency, response);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Cashfree order response", e);
        }
    }

    @Override
    public boolean verifyPayment(String orderId, String paymentId, String signature) {
        if (orderId == null || orderId.isBlank()) {
            return false;
        }
        if (!properties.isConfigured()) {
            return orderId.startsWith("order_dev_");
        }
        if (signature != null && !signature.isBlank() && paymentId != null && !paymentId.isBlank()) {
            String expected = PaymentCrypto.hmacSha256Hex(orderId + paymentId, properties.getSecretKey());
            if (PaymentCrypto.constantTimeEquals(expected, signature)) {
                return true;
            }
        }
        // Fallback: fetch order status from Cashfree
        try {
            String response = restClientBuilder
                    .baseUrl(properties.apiBaseUrl())
                    .build()
                    .get()
                    .uri("/orders/{orderId}", orderId)
                    .header("x-client-id", properties.getAppId())
                    .header("x-client-secret", properties.getSecretKey())
                    .header("x-api-version", API_VERSION)
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(response);
            String status = node.path("order_status").asText();
            return "PAID".equalsIgnoreCase(status);
        } catch (Exception e) {
            log.warn("Cashfree order verify failed for {}: {}", orderId, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signature) {
        if (payload == null || signature == null || signature.isBlank()) {
            return false;
        }
        String secret = properties.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            secret = properties.getSecretKey();
        }
        if (secret == null || secret.isBlank()) {
            if (!properties.isConfigured()) {
                log.warn("Cashfree webhook secret blank — accepting signature in dev mode");
                return true;
            }
            return false;
        }
        String expected = PaymentCrypto.hmacSha256Base64(payload, secret);
        return PaymentCrypto.constantTimeEquals(expected, signature);
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
