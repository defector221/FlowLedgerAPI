package com.flowledger.subscription.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.subscription.config.StripeProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class StripeProvider implements PaymentProvider {
    private final StripeProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public StripeProvider(StripeProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl("https://api.stripe.com").build();
    }

    @Override
    public String name() {
        return "stripe";
    }

    @Override
    public CreateOrderResult createOrder(CreateOrderRequest request) {
        if (!properties.isConfigured()) {
            String mockOrderId =
                    "pi_dev_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
            log.warn("Stripe keys blank — returning mock payment intent {}", mockOrderId);
            return new CreateOrderResult(
                    mockOrderId,
                    request.amount(),
                    request.currency(),
                    "{\"mock\":true,\"id\":\"" + mockOrderId + "\",\"client_secret\":\"" + mockOrderId
                            + "_secret_dev\"}");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("amount", String.valueOf(toMinorUnits(request.amount())));
        form.add("currency", (request.currency() == null ? "inr" : request.currency()).toLowerCase());
        form.add("automatic_payment_methods[enabled]", "true");
        if (request.receipt() != null) {
            form.add("metadata[receipt]", request.receipt());
        }
        if (request.notes() != null) {
            form.add("metadata[note]", request.notes());
        }

        String response = restClient
                .post()
                .uri("/v1/payment_intents")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(h -> h.setBearerAuth(properties.getSecretKey()))
                .body(form)
                .retrieve()
                .body(String.class);

        try {
            JsonNode node = objectMapper.readTree(response);
            String orderId = node.path("id").asText();
            BigDecimal amount = BigDecimal.valueOf(node.path("amount").asLong())
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            String currency = node.path("currency").asText("inr").toUpperCase();
            return new CreateOrderResult(orderId, amount, currency, response);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Stripe payment intent response", e);
        }
    }

    @Override
    public boolean verifyPayment(String orderId, String paymentId, String signature) {
        if (orderId == null || orderId.isBlank()) {
            return false;
        }
        if (!properties.isConfigured()) {
            return orderId.startsWith("pi_dev_");
        }
        // Client may send payment intent id as orderId; paymentId may be the same or a charge id.
        // Signature for Stripe client confirm is optional — re-fetch PaymentIntent status.
        try {
            String response = restClient
                    .get()
                    .uri("/v1/payment_intents/{id}", orderId)
                    .headers(h -> h.setBearerAuth(properties.getSecretKey()))
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(response);
            String status = node.path("status").asText();
            return "succeeded".equalsIgnoreCase(status);
        } catch (Exception e) {
            log.warn("Stripe payment intent verify failed for {}: {}", orderId, e.getMessage());
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
            if (!properties.isConfigured()) {
                log.warn("Stripe webhook secret blank — accepting signature in dev mode");
                return true;
            }
            return false;
        }

        Map<String, String> parts = parseStripeSignature(signature);
        String timestamp = parts.get("t");
        String v1 = parts.get("v1");
        if (timestamp == null || v1 == null) {
            return false;
        }
        String signedPayload = timestamp + "." + payload;
        String expected = hmacSha256Hex(signedPayload, secret);
        return PaymentCrypto.constantTimeEquals(expected, v1);
    }

    static long toMinorUnits(BigDecimal amount) {
        if (amount == null) {
            return 0L;
        }
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private static Map<String, String> parseStripeSignature(String header) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String part : header.split(",")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) {
                map.putIfAbsent(kv[0].trim(), kv[1].trim());
            }
        }
        return map;
    }

    private static String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC SHA256 failed", e);
        }
    }
}
