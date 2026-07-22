package com.flowledger.subscription.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.subscription.config.PayPalProperties;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class PayPalProvider implements PaymentProvider {
    private final PayPalProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    public PayPalProvider(PayPalProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClientBuilder = RestClient.builder();
    }

    @Override
    public String name() {
        return "paypal";
    }

    @Override
    public CreateOrderResult createOrder(CreateOrderRequest request) {
        if (!properties.isConfigured()) {
            String mockOrderId =
                    "ORDER-DEV-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
            log.warn("PayPal keys blank — returning mock order {}", mockOrderId);
            return new CreateOrderResult(mockOrderId, request.amount(), request.currency(), "{\"mock\":true}");
        }

        String accessToken = fetchAccessToken();
        String currency = request.currency() == null ? "USD" : request.currency();
        Map<String, Object> body = Map.of(
                "intent",
                "CAPTURE",
                "purchase_units",
                List.of(Map.of(
                        "reference_id",
                                request.receipt() == null ? UUID.randomUUID().toString() : request.receipt(),
                        "description", request.notes() == null ? "FlowLedger subscription" : request.notes(),
                        "amount",
                                Map.of(
                                        "currency_code",
                                        currency,
                                        "value",
                                        request.amount()
                                                .setScale(2, RoundingMode.HALF_UP)
                                                .toPlainString()))));

        String response = client().post()
                .uri("/v2/checkout/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> h.setBearerAuth(accessToken))
                .body(body)
                .retrieve()
                .body(String.class);

        try {
            JsonNode node = objectMapper.readTree(response);
            String orderId = node.path("id").asText();
            return new CreateOrderResult(orderId, request.amount(), currency, response);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse PayPal order response", e);
        }
    }

    @Override
    public boolean verifyPayment(String orderId, String paymentId, String signature) {
        if (orderId == null || orderId.isBlank()) {
            return false;
        }
        if (!properties.isConfigured()) {
            return orderId.startsWith("ORDER-DEV-");
        }
        try {
            String accessToken = fetchAccessToken();
            // Prefer capture when client sends a completed order; otherwise inspect status.
            String getResponse = client().get()
                    .uri("/v2/checkout/orders/{id}", orderId)
                    .headers(h -> h.setBearerAuth(accessToken))
                    .retrieve()
                    .body(String.class);
            JsonNode order = objectMapper.readTree(getResponse);
            String status = order.path("status").asText();
            if ("COMPLETED".equalsIgnoreCase(status)) {
                return true;
            }
            if ("APPROVED".equalsIgnoreCase(status)) {
                String captureResponse = client().post()
                        .uri("/v2/checkout/orders/{id}/capture", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .headers(h -> h.setBearerAuth(accessToken))
                        .body(Map.of())
                        .retrieve()
                        .body(String.class);
                JsonNode captured = objectMapper.readTree(captureResponse);
                return "COMPLETED".equalsIgnoreCase(captured.path("status").asText());
            }
            return false;
        } catch (Exception e) {
            log.warn("PayPal order verify/capture failed for {}: {}", orderId, e.getMessage());
            return false;
        }
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signatureHeader) {
        // PayPal uses multi-header verification; signatureHeader may be a JSON blob of headers
        // assembled by the controller, or blank in local/dev.
        if (payload == null) {
            return false;
        }
        if (!properties.isConfigured()
                || properties.getWebhookId() == null
                || properties.getWebhookId().isBlank()) {
            if (!properties.isConfigured()) {
                log.warn("PayPal webhook id/keys blank — accepting signature in dev mode");
                return true;
            }
            return false;
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        try {
            JsonNode headers = objectMapper.readTree(signatureHeader);
            String accessToken = fetchAccessToken();
            Map<String, Object> body = Map.of(
                    "auth_algo", text(headers, "auth_algo"),
                    "cert_url", text(headers, "cert_url"),
                    "transmission_id", text(headers, "transmission_id"),
                    "transmission_sig", text(headers, "transmission_sig"),
                    "transmission_time", text(headers, "transmission_time"),
                    "webhook_id", properties.getWebhookId(),
                    "webhook_event", objectMapper.readTree(payload));

            String response = client().post()
                    .uri("/v1/notifications/verify-webhook-signature")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> h.setBearerAuth(accessToken))
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(response);
            return "SUCCESS".equalsIgnoreCase(node.path("verification_status").asText());
        } catch (Exception e) {
            log.warn("PayPal webhook verification failed: {}", e.getMessage());
            return false;
        }
    }

    private String fetchAccessToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "client_credentials");
        String response = client().post()
                .uri("/v1/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(h -> h.setBasicAuth(properties.getClientId(), properties.getClientSecret()))
                .body(form)
                .retrieve()
                .body(String.class);
        try {
            return objectMapper.readTree(response).path("access_token").asText();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to obtain PayPal access token", e);
        }
    }

    private RestClient client() {
        return restClientBuilder.baseUrl(properties.apiBaseUrl()).build();
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }
}
