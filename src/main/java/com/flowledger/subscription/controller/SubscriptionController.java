package com.flowledger.subscription.controller;

import com.flowledger.common.security.SecurityUtils;
import com.flowledger.subscription.dto.SubscriptionDtos.CheckoutRequest;
import com.flowledger.subscription.dto.SubscriptionDtos.CheckoutResponse;
import com.flowledger.subscription.dto.SubscriptionDtos.CurrentSubscriptionResponse;
import com.flowledger.subscription.dto.SubscriptionDtos.InvoiceResponse;
import com.flowledger.subscription.dto.SubscriptionDtos.PlanResponse;
import com.flowledger.subscription.dto.SubscriptionDtos.UsageResponse;
import com.flowledger.subscription.dto.SubscriptionDtos.VerifyPaymentRequest;
import com.flowledger.subscription.service.SubscriptionBillingService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/subscriptions")
@PreAuthorize("hasRole('ORGANIZATION_ADMIN')")
public class SubscriptionController {
    private final SubscriptionBillingService billing;

    public SubscriptionController(SubscriptionBillingService billing) {
        this.billing = billing;
    }

    @GetMapping("/plans")
    public List<PlanResponse> plans() {
        return billing.listPlans();
    }

    @GetMapping("/current")
    public CurrentSubscriptionResponse current() {
        return billing.getCurrent(SecurityUtils.currentOrganizationId());
    }

    @PostMapping("/checkout")
    public CheckoutResponse checkout(@Valid @RequestBody CheckoutRequest request) {
        return billing.checkout(SecurityUtils.currentOrganizationId(), request);
    }

    @PostMapping("/upgrade")
    public CheckoutResponse upgrade(@Valid @RequestBody CheckoutRequest request) {
        return billing.upgrade(SecurityUtils.currentOrganizationId(), request);
    }

    @PostMapping("/cancel")
    public CurrentSubscriptionResponse cancel(@RequestParam(defaultValue = "false") boolean immediate) {
        return billing.cancel(SecurityUtils.currentOrganizationId(), immediate);
    }

    @GetMapping("/invoices")
    public List<InvoiceResponse> invoices() {
        return billing.listInvoices(SecurityUtils.currentOrganizationId());
    }

    @GetMapping("/usage")
    public UsageResponse usage() {
        return billing.usage(SecurityUtils.currentUserId(), SecurityUtils.currentOrganizationId());
    }

    @PostMapping("/verify-payment")
    public CurrentSubscriptionResponse verifyPayment(@Valid @RequestBody VerifyPaymentRequest request) {
        return billing.verifyPayment(SecurityUtils.currentOrganizationId(), request);
    }
}
