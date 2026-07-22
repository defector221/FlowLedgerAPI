package com.flowledger.subscription.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.subscription.config.RazorpayProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class RazorpayProvider implements PaymentProvider {
    private final RazorpayProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public RazorpayProvider(RazorpayProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient =
                RestClient.builder().baseUrl("https://api.razorpay.com/v1").build();
    }

    @Override
    public String name() {
        return "razorpay";
    }

    @Override
    public CreateOrderResult createOrder(CreateOrderRequest request) {
        if (!properties.isConfigured()) {
            String mockOrderId =
                    "order_dev_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
            log.warn("Razorpay keys blank — returning mock order {}", mockOrderId);
            return new CreateOrderResult(mockOrderId, request.amount(), request.currency(), "{\"mock\":true}");
        }

        long amountPaise = toMinorUnits(request.amount());
        Map<String, Object> body = Map.of(
                "amount", amountPaise,
                "currency", request.currency() == null ? "INR" : request.currency(),
                "receipt", request.receipt() == null ? UUID.randomUUID().toString() : request.receipt(),
                "notes", request.notes() == null ? Map.of() : Map.of("note", request.notes()));

        String response = restClient
                .post()
                .uri("/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .headers(h -> h.setBasicAuth(properties.getKeyId(), properties.getKeySecret()))
                .body(body)
                .retrieve()
                .body(String.class);

        try {
            var node = objectMapper.readTree(response);
            String orderId = node.path("id").asText();
            BigDecimal amount = BigDecimal.valueOf(node.path("amount").asLong())
                    .divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
            String currency = node.path("currency").asText("INR");
            return new CreateOrderResult(orderId, amount, currency, response);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Razorpay order response", e);
        }
    }

    @Override
    public boolean verifyPayment(String orderId, String paymentId, String signature) {
        if (orderId == null || paymentId == null || signature == null) {
            return false;
        }
        if (!properties.isConfigured()) {
            // Dev mode: accept any non-blank signature for mock orders
            return orderId.startsWith("order_dev_");
        }
        String payload = orderId + "|" + paymentId;
        String expected = hmacSha256Hex(payload, properties.getKeySecret());
        return constantTimeEquals(expected, signature);
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signature) {
        if (payload == null || signature == null || signature.isBlank()) {
            return false;
        }
        String secret = properties.getWebhookSecret();
        if (secret == null || secret.isBlank()) {
            if (!properties.isConfigured()) {
                log.warn("Razorpay webhook secret blank — accepting signature in dev mode");
                return true;
            }
            return false;
        }
        String expected = hmacSha256Hex(payload, secret);
        return constantTimeEquals(expected, signature);
    }

    static long toMinorUnits(BigDecimal amount) {
        if (amount == null) {
            return 0L;
        }
        return amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    static String hmacSha256Hex(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC SHA256 failed", e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] left = a.getBytes(StandardCharsets.UTF_8);
        byte[] right = b.getBytes(StandardCharsets.UTF_8);
        if (left.length != right.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < left.length; i++) {
            result |= left[i] ^ right[i];
        }
        return result == 0;
    }
}
