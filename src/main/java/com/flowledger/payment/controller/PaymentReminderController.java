package com.flowledger.payment.controller;

import com.flowledger.payment.service.PaymentReminderService;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payment-reminders")
public class PaymentReminderController {
    private final PaymentReminderService service;

    public PaymentReminderController(PaymentReminderService service) {
        this.service = service;
    }

    @PostMapping("/invoices/{invoiceId}/send")
    public UUID sendNow(@PathVariable UUID invoiceId) {
        return service.sendNow(invoiceId);
    }
}
