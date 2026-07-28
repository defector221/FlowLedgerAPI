package com.flowledger.commerce.api;

import com.flowledger.commerce.payment.CommercePaymentWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/commerce/payments/webhooks")
public class CommercePaymentWebhookController {
    private final CommercePaymentWebhookService webhooks;

    public CommercePaymentWebhookController(CommercePaymentWebhookService webhooks) {
        this.webhooks = webhooks;
    }

    @PostMapping("/{provider}")
    public ResponseEntity<Void> handle(
            @PathVariable String provider,
            @RequestBody String payload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String razorpaySignature,
            @RequestHeader(value = "Stripe-Signature", required = false) String stripeSignature) {
        String signature = razorpaySignature != null ? razorpaySignature : stripeSignature;
        webhooks.handleProvider(provider, payload, signature);
        return ResponseEntity.ok().build();
    }
}
