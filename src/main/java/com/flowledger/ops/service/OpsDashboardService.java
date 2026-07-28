package com.flowledger.ops.service;

import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.subscription.entity.OrganizationSubscription;
import com.flowledger.subscription.entity.SubscriptionPlan;
import com.flowledger.subscription.repository.OrganizationSubscriptionRepository;
import com.flowledger.subscription.repository.SubscriptionPlanRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpsDashboardService {
    private final OrganizationRepository organizations;
    private final OrganizationSubscriptionRepository subscriptions;
    private final SubscriptionPlanRepository plans;
    private final HealthEndpoint healthEndpoint;

    public OpsDashboardService(
            OrganizationRepository organizations,
            OrganizationSubscriptionRepository subscriptions,
            SubscriptionPlanRepository plans,
            HealthEndpoint healthEndpoint) {
        this.organizations = organizations;
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.healthEndpoint = healthEndpoint;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> summary() {
        Map<String, Object> out = new LinkedHashMap<>();
        long total = organizations.count();
        long active = organizations.countByLifecycleStatus("ACTIVE");
        long suspended = organizations.countByLifecycleStatus("SUSPENDED");
        long archived = organizations.countByLifecycleStatus("ARCHIVED");

        // Keys match FlowLedgerPlatformUI DashboardSummary
        out.put("totalOrganizations", total);
        out.put("tenantCount", total);
        out.put("activeOrganizations", active);
        out.put("suspendedOrganizations", suspended);
        out.put("archivedOrganizations", archived);
        out.put("deletedOrganizations", 0);

        Map<String, Long> orgsByStatus = new LinkedHashMap<>();
        orgsByStatus.put("ACTIVE", active);
        orgsByStatus.put("SUSPENDED", suspended);
        orgsByStatus.put("ARCHIVED", archived);
        out.put("orgsByStatus", orgsByStatus);

        List<OrganizationSubscription> allSubs = subscriptions.findAll();
        long paid = allSubs.stream().filter(s -> "ACTIVE".equalsIgnoreCase(s.getStatus())).count();
        long trial = allSubs.stream().filter(s -> "TRIAL".equalsIgnoreCase(s.getStatus())).count();
        out.put("totalSubscriptions", allSubs.size());
        out.put("paidOrganizations", paid);
        out.put("trialOrganizations", trial);

        BigDecimal mrr = BigDecimal.ZERO;
        for (OrganizationSubscription sub : allSubs) {
            if (!"ACTIVE".equalsIgnoreCase(sub.getStatus())) {
                continue;
            }
            SubscriptionPlan plan = sub.getPlan();
            if (plan == null) {
                continue;
            }
            if ("YEARLY".equalsIgnoreCase(sub.getBillingCycle())) {
                mrr = mrr.add(plan.getPriceYearly().divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP));
            } else {
                mrr = mrr.add(plan.getPriceMonthly() != null ? plan.getPriceMonthly() : BigDecimal.ZERO);
            }
        }
        out.put("mrr", mrr);
        out.put("arr", mrr.multiply(BigDecimal.valueOf(12)));
        out.put("planCount", plans.count());

        String healthStatus = "UNKNOWN";
        try {
            healthStatus = healthEndpoint.health().getStatus().getCode();
        } catch (Exception ignored) {
            // leave UNKNOWN
        }
        out.put("health", healthStatus);
        out.put("healthStatus", healthStatus);
        return out;
    }
}
