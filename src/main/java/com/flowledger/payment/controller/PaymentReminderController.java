package com.flowledger.payment.controller;

import com.flowledger.payment.dto.PaymentReminderDtos.ReminderRuleRequest;
import com.flowledger.payment.dto.PaymentReminderDtos.ReminderRuleResponse;
import com.flowledger.payment.dto.PaymentReminderDtos.SendReminderRequest;
import com.flowledger.payment.dto.PaymentReminderDtos.SendReminderResponse;
import com.flowledger.payment.service.PaymentReminderService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payment-reminders")
public class PaymentReminderController {
    private final PaymentReminderService service;

    public PaymentReminderController(PaymentReminderService service) {
        this.service = service;
    }

    @PostMapping("/invoices/{invoiceId}/send")
    public SendReminderResponse sendNow(
            @PathVariable UUID invoiceId, @RequestBody(required = false) SendReminderRequest request) {
        return service.sendNow(invoiceId, request);
    }

    @GetMapping("/rules")
    public List<ReminderRuleResponse> listRules() {
        return service.listRules();
    }

    @PostMapping("/rules")
    public ReminderRuleResponse createRule(@Valid @RequestBody ReminderRuleRequest request) {
        return service.createRule(request);
    }

    @PutMapping("/rules/{id}")
    public ReminderRuleResponse updateRule(@PathVariable UUID id, @Valid @RequestBody ReminderRuleRequest request) {
        return service.updateRule(id, request);
    }

    @DeleteMapping("/rules/{id}")
    public void deleteRule(@PathVariable UUID id) {
        service.deleteRule(id);
    }
}
