package com.flowledger.subscription.service;

import com.flowledger.auth.entity.OrganizationMembership;
import com.flowledger.auth.entity.Role;
import com.flowledger.auth.repository.OrganizationMembershipRepository;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.sales.repository.SalesInvoiceRepository;
import com.flowledger.subscription.dto.BillingDtos.CurrentBilling;
import com.flowledger.subscription.dto.BillingDtos.PlanInfo;
import com.flowledger.subscription.dto.BillingDtos.UsageInfo;
import com.flowledger.subscription.entity.OrganizationSubscription;
import com.flowledger.subscription.entity.SubscriptionPlan;
import com.flowledger.subscription.entity.UserSubscription;
import com.flowledger.subscription.repository.OrganizationSubscriptionRepository;
import com.flowledger.subscription.repository.SubscriptionPlanRepository;
import com.flowledger.subscription.repository.UserSubscriptionRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional
public class SubscriptionService {
    private final UserSubscriptionRepository subscriptions;
    private final SubscriptionPlanRepository plans;
    private final OrganizationMembershipRepository memberships;
    private final OrganizationSubscriptionRepository organizationSubscriptions;
    private final SalesInvoiceRepository salesInvoices;

    public SubscriptionService(
            UserSubscriptionRepository subscriptions,
            SubscriptionPlanRepository plans,
            OrganizationMembershipRepository memberships,
            OrganizationSubscriptionRepository organizationSubscriptions,
            SalesInvoiceRepository salesInvoices) {
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.memberships = memberships;
        this.organizationSubscriptions = organizationSubscriptions;
        this.salesInvoices = salesInvoices;
    }

    public void ensureDefaultSubscription(UUID userId, String planCode) {
        SubscriptionPlan plan = plans.findByCode(planCode)
                .orElseThrow(() -> new BusinessException("Subscription plan unavailable: " + planCode));
        if (subscriptions.findByUserId(userId).isEmpty()) {
            UserSubscription subscription = new UserSubscription();
            subscription.setUserId(userId);
            subscription.setPlanId(plan.getId());
            subscription.setStatus("ACTIVE");
            subscriptions.save(subscription);
        }
        ensureOrganizationSubscriptionsForUser(userId, plan);
    }

    /** Ensures every org the user admins has an OrganizationSubscription (SoT). */
    public void ensureOrganizationSubscription(UUID organizationId, String planCode) {
        if (organizationSubscriptions.findByOrganizationId(organizationId).isPresent()) {
            return;
        }
        SubscriptionPlan plan = plans.findByCode(planCode)
                .orElseThrow(() -> new BusinessException("Subscription plan unavailable: " + planCode));
        OrganizationSubscription orgSub = new OrganizationSubscription();
        orgSub.setOrganizationId(organizationId);
        orgSub.setPlanId(plan.getId());
        orgSub.setBillingCycle("MONTHLY");
        orgSub.setStatus("ACTIVE");
        organizationSubscriptions.save(orgSub);
    }

    private void ensureOrganizationSubscriptionsForUser(UUID userId, SubscriptionPlan plan) {
        memberships.findByUserIdAndStatus(userId, "ACTIVE").stream()
                .filter(this::isOrgAdmin)
                .forEach(m -> {
                    if (organizationSubscriptions.findByOrganizationId(m.getOrganizationId()).isEmpty()) {
                        OrganizationSubscription orgSub = new OrganizationSubscription();
                        orgSub.setOrganizationId(m.getOrganizationId());
                        orgSub.setPlanId(plan.getId());
                        orgSub.setBillingCycle("MONTHLY");
                        orgSub.setStatus("ACTIVE");
                        organizationSubscriptions.save(orgSub);
                    }
                });
    }

    public void checkCanCreateOrganization(UUID userId) {
        SubscriptionPlan plan = resolvePlanForUser(userId);
        long ownedOrgs = memberships.findByUserIdAndStatus(userId, "ACTIVE").stream()
                .filter(this::isOrgAdmin)
                .count();
        if (ownedOrgs >= plan.getMaxOrganizations()) {
            throw new BusinessException(
                    "Organization limit reached for plan " + plan.getCode() + " (" + plan.getMaxOrganizations() + ")");
        }
    }

    public void checkCanInvite(UUID organizationId) {
        SubscriptionPlan plan = resolvePlanForOrganization(organizationId);
        long members = memberships.findByOrganizationId(organizationId).stream()
                .filter(m -> !"INACTIVE".equals(m.getStatus()))
                .count();
        if (members >= plan.getMaxUsersPerOrg()) {
            throw new BusinessException(
                    "User invite limit reached for plan " + plan.getCode() + " (" + plan.getMaxUsersPerOrg() + ")");
        }
    }

    public void checkCanCreateInvoice(UUID organizationId) {
        SubscriptionPlan plan = resolvePlanForOrganization(organizationId);
        YearMonth month = YearMonth.now();
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        long count = salesInvoices.countByOrganizationIdAndInvoiceDateBetween(organizationId, start, end);
        if (count >= plan.getMaxInvoicesPerMonth()) {
            throw new BusinessException("Invoice limit reached for plan "
                    + plan.getCode()
                    + " ("
                    + plan.getMaxInvoicesPerMonth()
                    + " per month)");
        }
    }

    @Transactional(readOnly = true)
    public CurrentBilling getCurrentBilling(UUID userId, UUID organizationId) {
        OrganizationSubscription orgSub = organizationSubscriptions.findByOrganizationId(organizationId).orElse(null);
        SubscriptionPlan plan;
        String status;
        if (orgSub != null && isActiveLike(orgSub.getStatus())) {
            plan = requireOrgPlan(orgSub);
            status = orgSub.getStatus();
        } else {
            UserSubscription subscription = subscriptions.findByUserId(userId).orElse(null);
            plan = subscription == null ? resolvePlanForUser(userId) : requirePlan(subscription);
            status = subscription == null ? "NONE" : subscription.getStatus();
        }

        int organizationCount = (int) memberships.findByUserIdAndStatus(userId, "ACTIVE").stream()
                .filter(this::isOrgAdmin)
                .count();
        int userCount = (int) memberships.findByOrganizationId(organizationId).stream()
                .filter(m -> !"INACTIVE".equals(m.getStatus()))
                .count();
        YearMonth month = YearMonth.now();
        int invoiceCount = (int) salesInvoices.countByOrganizationIdAndInvoiceDateBetween(
                organizationId, month.atDay(1), month.atEndOfMonth());

        return new CurrentBilling(
                toPlanInfo(plan),
                status,
                new UsageInfo(
                        organizationCount,
                        plan.getMaxOrganizations(),
                        userCount,
                        plan.getMaxUsersPerOrg(),
                        invoiceCount,
                        plan.getMaxInvoicesPerMonth()));
    }

    @Transactional(readOnly = true)
    public SubscriptionPlan resolvePlanForOrganization(UUID organizationId) {
        var orgSub = organizationSubscriptions.findByOrganizationId(organizationId);
        if (orgSub.isPresent() && isActiveLike(orgSub.get().getStatus())) {
            return requireOrgPlan(orgSub.get());
        }
        List<OrganizationMembership> admins = memberships.findByOrganizationId(organizationId).stream()
                .filter(m -> "ACTIVE".equals(m.getStatus()))
                .filter(this::isOrgAdmin)
                .toList();
        for (OrganizationMembership membership : admins) {
            var sub = subscriptions.findByUserId(membership.getUserId());
            if (sub.isPresent()) {
                return requirePlan(sub.get());
            }
        }
        return plans.findByCode("FREE")
                .orElseThrow(() -> new BusinessException("Default subscription plan unavailable"));
    }

    @Transactional(readOnly = true)
    public SubscriptionPlan resolvePlanForUser(UUID userId) {
        return subscriptions.findByUserId(userId).map(this::requirePlan).orElseGet(() -> plans.findByCode("FREE")
                .orElseThrow(() -> new BusinessException("Default subscription plan unavailable")));
    }

    public static PlanInfo toPlanInfo(SubscriptionPlan plan) {
        return new PlanInfo(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getDescription(),
                plan.getMaxOrganizations(),
                plan.getMaxUsersPerOrg(),
                plan.getMaxInvoicesPerMonth(),
                plan.getPriceMonthly(),
                plan.getPriceYearly());
    }

    private SubscriptionPlan requireOrgPlan(OrganizationSubscription subscription) {
        if (subscription.getPlan() != null) {
            return subscription.getPlan();
        }
        return plans.findById(subscription.getPlanId())
                .orElseThrow(() -> new BusinessException("Subscription plan not found"));
    }

    private SubscriptionPlan requirePlan(UserSubscription subscription) {
        if (subscription.getPlan() != null) {
            return subscription.getPlan();
        }
        return plans.findById(subscription.getPlanId())
                .orElseThrow(() -> new BusinessException("Subscription plan not found"));
    }

    private boolean isActiveLike(String status) {
        return "ACTIVE".equals(status) || "TRIAL".equals(status);
    }

    private boolean isOrgAdmin(OrganizationMembership membership) {
        return membership.getRoles().stream().map(Role::getCode).anyMatch("ORGANIZATION_ADMIN"::equals);
    }
}
