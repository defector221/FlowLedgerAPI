package com.flowledger.subscription.service;

import com.flowledger.auth.entity.OrganizationMembership;
import com.flowledger.auth.entity.Role;
import com.flowledger.auth.repository.OrganizationMembershipRepository;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.util.DocumentNumberService;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.subscription.domain.BillingCycle;
import com.flowledger.subscription.entity.OrganizationSubscription;
import com.flowledger.subscription.entity.PaymentTransaction;
import com.flowledger.subscription.entity.SubscriptionInvoice;
import com.flowledger.subscription.entity.SubscriptionPlan;
import com.flowledger.subscription.entity.UserSubscription;
import com.flowledger.subscription.repository.OrganizationSubscriptionRepository;
import com.flowledger.subscription.repository.PaymentTransactionRepository;
import com.flowledger.subscription.repository.SubscriptionInvoiceRepository;
import com.flowledger.subscription.repository.UserSubscriptionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional
public class SubscriptionActivationService {
    private final OrganizationSubscriptionRepository organizationSubscriptions;
    private final UserSubscriptionRepository userSubscriptions;
    private final OrganizationMembershipRepository memberships;
    private final PaymentTransactionRepository paymentTransactions;
    private final SubscriptionInvoiceRepository subscriptionInvoices;
    private final OrganizationRepository organizations;
    private final DocumentNumberService documentNumbers;

    public SubscriptionActivationService(
            OrganizationSubscriptionRepository organizationSubscriptions,
            UserSubscriptionRepository userSubscriptions,
            OrganizationMembershipRepository memberships,
            PaymentTransactionRepository paymentTransactions,
            SubscriptionInvoiceRepository subscriptionInvoices,
            OrganizationRepository organizations,
            DocumentNumberService documentNumbers) {
        this.organizationSubscriptions = organizationSubscriptions;
        this.userSubscriptions = userSubscriptions;
        this.memberships = memberships;
        this.paymentTransactions = paymentTransactions;
        this.subscriptionInvoices = subscriptionInvoices;
        this.organizations = organizations;
        this.documentNumbers = documentNumbers;
    }

    public OrganizationSubscription activate(
            UUID organizationId,
            SubscriptionPlan plan,
            BillingCycle billingCycle,
            PaymentTransaction transaction,
            String paymentProvider,
            String paymentReference) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime nextBilling = billingCycle == BillingCycle.YEARLY ? now.plusYears(1) : now.plusMonths(1);

        OrganizationSubscription orgSub =
                organizationSubscriptions.findByOrganizationId(organizationId).orElseGet(OrganizationSubscription::new);
        orgSub.setOrganizationId(organizationId);
        orgSub.setPlanId(plan.getId());
        orgSub.setBillingCycle(billingCycle.name());
        orgSub.setStatus("ACTIVE");
        orgSub.setStartDate(now);
        orgSub.setEndDate(null);
        orgSub.setNextBillingDate(nextBilling);
        orgSub.setAutoRenew(true);
        orgSub.setPaymentProvider(paymentProvider);
        orgSub.setPaymentReference(paymentReference);
        orgSub = organizationSubscriptions.save(orgSub);

        syncOwningAdminUserSubscription(organizationId, plan.getId());

        if (transaction != null) {
            transaction.setStatus("PAID");
            if (paymentReference != null) {
                transaction.setPaymentId(paymentReference);
            }
            paymentTransactions.save(transaction);
        }
        createSubscriptionInvoice(organizationId, plan, transaction);

        log.info("Activated organization {} on plan {} ({})", organizationId, plan.getCode(), billingCycle);
        return orgSub;
    }

    public void syncOwningAdminUserSubscription(UUID organizationId, UUID planId) {
        UUID adminUserId = findPrimaryAdminUserId(organizationId);
        if (adminUserId == null) {
            log.warn("No ORGANIZATION_ADMIN found for org {} while syncing user subscription", organizationId);
            return;
        }
        UserSubscription userSub = userSubscriptions.findByUserId(adminUserId).orElseGet(() -> {
            UserSubscription created = new UserSubscription();
            created.setUserId(adminUserId);
            return created;
        });
        userSub.setPlanId(planId);
        userSub.setStatus("ACTIVE");
        userSubscriptions.save(userSub);
    }

    private void createSubscriptionInvoice(UUID organizationId, SubscriptionPlan plan, PaymentTransaction transaction) {
        Organization org = organizations
                .findById(organizationId)
                .orElseThrow(() -> new BusinessException("Organization not found"));
        BigDecimal amount = transaction != null ? transaction.getAmount() : BigDecimal.ZERO;
        String invoiceNumber = documentNumbers.next(
                organizationId,
                "SUBSCRIPTION_INVOICE",
                "SUB",
                "{PREFIX}/{FY}/{SEQ:6}",
                org.getFinancialYearStart() == null ? "04-01" : org.getFinancialYearStart(),
                LocalDate.now());

        SubscriptionInvoice invoice = new SubscriptionInvoice();
        invoice.setOrganizationId(organizationId);
        invoice.setInvoiceNumber(invoiceNumber);
        invoice.setAmount(amount);
        invoice.setGst(BigDecimal.ZERO);
        invoice.setDiscount(BigDecimal.ZERO);
        invoice.setTotal(amount);
        invoice.setPaidAt(OffsetDateTime.now());
        if (transaction != null) {
            invoice.setPaymentTransactionId(transaction.getId());
        }
        subscriptionInvoices.save(invoice);
    }

    private UUID findPrimaryAdminUserId(UUID organizationId) {
        return memberships.findByOrganizationId(organizationId).stream()
                .filter(m -> "ACTIVE".equals(m.getStatus()))
                .filter(this::isOrgAdmin)
                .sorted(Comparator.comparing(
                        OrganizationMembership::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(OrganizationMembership::getUserId)
                .findFirst()
                .orElse(null);
    }

    private boolean isOrgAdmin(OrganizationMembership membership) {
        return membership.getRoles().stream().map(Role::getCode).anyMatch("ORGANIZATION_ADMIN"::equals);
    }
}
