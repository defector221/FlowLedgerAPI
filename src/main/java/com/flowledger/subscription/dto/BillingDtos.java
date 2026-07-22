package com.flowledger.subscription.dto;

import java.math.BigDecimal;
import java.util.UUID;

public final class BillingDtos {
    private BillingDtos() {}

    public record PlanInfo(
            UUID id,
            String code,
            String name,
            String description,
            int maxOrganizations,
            int maxUsersPerOrg,
            int maxInvoicesPerMonth,
            BigDecimal priceMonthly,
            BigDecimal priceYearly) {}

    public record UsageInfo(
            int organizationCount,
            int organizationLimit,
            int userCount,
            int userLimit,
            int invoiceCount,
            int invoiceLimit) {}

    public record CurrentBilling(PlanInfo plan, String subscriptionStatus, UsageInfo usage) {}
}
