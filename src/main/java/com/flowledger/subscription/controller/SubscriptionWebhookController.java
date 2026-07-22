package com.flowledger.subscription.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.flowledger.subscription.service.SubscriptionWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subscriptions/webhooks")
public class SubscriptionWebhookController {
    private final SubscriptionWebhookService webhooks;
    private final ObjectMapper objectMapper;

    public SubscriptionWebhookController(SubscriptionWebhookService webhooks, ObjectMapper objectMapper) {
        this.webhooks = webhooks;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/razorpay")
    public ResponseEntity<Void> razorpay(
            @RequestBody String payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        webhooks.handleProvider("razorpay", payload, signature);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/stripe")
    public ResponseEntity<Void> stripe(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String signature) {
        webhooks.handleProvider("stripe", payload, signature);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/cashfree")
    public ResponseEntity<Void> cashfree(
            @RequestBody String payload,
            @RequestHeader(value = "x-webhook-signature", required = false) String signature) {
        webhooks.handleProvider("cashfree", payload, signature);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/paypal")
    public ResponseEntity<Void> paypal(
            @RequestBody String payload,
            @RequestHeader(value = "PAYPAL-AUTH-ALGO", required = false) String authAlgo,
            @RequestHeader(value = "PAYPAL-CERT-URL", required = false) String certUrl,
            @RequestHeader(value = "PAYPAL-TRANSMISSION-ID", required = false) String transmissionId,
            @RequestHeader(value = "PAYPAL-TRANSMISSION-SIG", required = false) String transmissionSig,
            @RequestHeader(value = "PAYPAL-TRANSMISSION-TIME", required = false) String transmissionTime) {
        ObjectNode headers = objectMapper.createObjectNode();
        headers.put("auth_algo", authAlgo == null ? "" : authAlgo);
        headers.put("cert_url", certUrl == null ? "" : certUrl);
        headers.put("transmission_id", transmissionId == null ? "" : transmissionId);
        headers.put("transmission_sig", transmissionSig == null ? "" : transmissionSig);
        headers.put("transmission_time", transmissionTime == null ? "" : transmissionTime);
        webhooks.handleProvider("paypal", payload, headers.toString());
        return ResponseEntity.ok().build();
    }
}
