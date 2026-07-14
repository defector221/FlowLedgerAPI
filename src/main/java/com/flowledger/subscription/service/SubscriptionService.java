package com.flowledger.subscription.service;

import com.flowledger.auth.entity.OrganizationMembership;
import com.flowledger.auth.entity.Role;
import com.flowledger.auth.repository.OrganizationMembershipRepository;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.subscription.dto.BillingDtos.CurrentBilling;
import com.flowledger.subscription.dto.BillingDtos.PlanInfo;
import com.flowledger.subscription.dto.BillingDtos.UsageInfo;
import com.flowledger.subscription.entity.SubscriptionPlan;
import com.flowledger.subscription.entity.UserSubscription;
import com.flowledger.subscription.repository.SubscriptionPlanRepository;
import com.flowledger.subscription.repository.UserSubscriptionRepository;
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

    public SubscriptionService(
            UserSubscriptionRepository subscriptions,
            SubscriptionPlanRepository plans,
            OrganizationMembershipRepository memberships) {
        this.subscriptions = subscriptions;
        this.plans = plans;
        this.memberships = memberships;
    }

    public void ensureDefaultSubscription(UUID userId, String planCode) {
        if (subscriptions.findByUserId(userId).isPresent()) {
            return;
        }
        SubscriptionPlan plan = plans.findByCode(planCode)
                .orElseThrow(() -> new BusinessException("Subscription plan unavailable: " + planCode));
        UserSubscription subscription = new UserSubscription();
        subscription.setUserId(userId);
        subscription.setPlanId(plan.getId());
        subscription.setStatus("ACTIVE");
        subscriptions.save(subscription);
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

    @Transactional(readOnly = true)
    public CurrentBilling getCurrentBilling(UUID userId, UUID organizationId) {
        UserSubscription subscription = subscriptions.findByUserId(userId).orElse(null);
        SubscriptionPlan plan = subscription == null ? resolvePlanForUser(userId) : requirePlan(subscription);
        String status = subscription == null ? "NONE" : subscription.getStatus();

        int organizationCount = (int) memberships.findByUserIdAndStatus(userId, "ACTIVE").stream()
                .filter(this::isOrgAdmin)
                .count();
        int userCount = (int) memberships.findByOrganizationId(organizationId).stream()
                .filter(m -> !"INACTIVE".equals(m.getStatus()))
                .count();

        return new CurrentBilling(
                new PlanInfo(
                        plan.getId(),
                        plan.getCode(),
                        plan.getName(),
                        plan.getDescription(),
                        plan.getMaxOrganizations(),
                        plan.getMaxUsersPerOrg(),
                        plan.getMaxInvoicesPerMonth(),
                        plan.getPriceMonthly()),
                status,
                new UsageInfo(organizationCount, plan.getMaxOrganizations(), userCount, plan.getMaxUsersPerOrg()));
    }

    private SubscriptionPlan resolvePlanForOrganization(UUID organizationId) {
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

    private SubscriptionPlan resolvePlanForUser(UUID userId) {
        return subscriptions.findByUserId(userId).map(this::requirePlan).orElseGet(() -> plans.findByCode("FREE")
                .orElseThrow(() -> new BusinessException("Default subscription plan unavailable")));
    }

    private SubscriptionPlan requirePlan(UserSubscription subscription) {
        if (subscription.getPlan() != null) {
            return subscription.getPlan();
        }
        return plans.findById(subscription.getPlanId())
                .orElseThrow(() -> new BusinessException("Subscription plan not found"));
    }

    private boolean isOrgAdmin(OrganizationMembership membership) {
        return membership.getRoles().stream().map(Role::getCode).anyMatch("ORGANIZATION_ADMIN"::equals);
    }
}
