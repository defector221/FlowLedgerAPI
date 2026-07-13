package com.flowledger.subscription.controller;

import com.flowledger.common.security.SecurityUtils;
import com.flowledger.subscription.dto.BillingDtos.CurrentBilling;
import com.flowledger.subscription.service.SubscriptionService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {
    private final SubscriptionService subscriptions;

    public BillingController(SubscriptionService subscriptions) {
        this.subscriptions = subscriptions;
    }

    @GetMapping("/current")
    @PreAuthorize("hasRole('ORGANIZATION_ADMIN')")
    public CurrentBilling current() {
        return subscriptions.getCurrentBilling(SecurityUtils.currentUserId(), SecurityUtils.currentOrganizationId());
    }
}
