package com.flowledger.subscription.integration;

import java.math.BigDecimal;

public interface PaymentProvider {
    String name();

    CreateOrderResult createOrder(CreateOrderRequest request);

    boolean verifyPayment(String orderId, String paymentId, String signature);

    boolean verifyWebhookSignature(String payload, String signature);

    record CreateOrderRequest(BigDecimal amount, String currency, String receipt, String notes) {}

    record CreateOrderResult(String orderId, BigDecimal amount, String currency, String rawJson) {}
}
