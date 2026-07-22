package com.flowledger.subscription.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.auth.entity.OrganizationMembership;
import com.flowledger.auth.entity.Role;
import com.flowledger.auth.repository.OrganizationMembershipRepository;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.sales.repository.SalesInvoiceRepository;
import com.flowledger.subscription.config.CashfreeProperties;
import com.flowledger.subscription.config.PayPalProperties;
import com.flowledger.subscription.config.RazorpayProperties;
import com.flowledger.subscription.config.StripeProperties;
import com.flowledger.subscription.domain.BillingCycle;
import com.flowledger.subscription.dto.SubscriptionDtos.CheckoutRequest;
import com.flowledger.subscription.dto.SubscriptionDtos.CheckoutResponse;
import com.flowledger.subscription.dto.SubscriptionDtos.CurrentSubscriptionResponse;
import com.flowledger.subscription.dto.SubscriptionDtos.InvoiceResponse;
import com.flowledger.subscription.dto.SubscriptionDtos.PlanResponse;
import com.flowledger.subscription.dto.SubscriptionDtos.UsageResponse;
import com.flowledger.subscription.dto.SubscriptionDtos.VerifyPaymentRequest;
import com.flowledger.subscription.entity.OrganizationSubscription;
import com.flowledger.subscription.entity.PaymentTransaction;
import com.flowledger.subscription.entity.SubscriptionInvoice;
import com.flowledger.subscription.entity.SubscriptionPlan;
import com.flowledger.subscription.integration.PaymentProvider;
import com.flowledger.subscription.integration.PaymentProvider.CreateOrderRequest;
import com.flowledger.subscription.integration.PaymentProviderRegistry;
import com.flowledger.subscription.repository.OrganizationSubscriptionRepository;
import com.flowledger.subscription.repository.PaymentTransactionRepository;
import com.flowledger.subscription.repository.SubscriptionInvoiceRepository;
import com.flowledger.subscription.repository.SubscriptionPlanRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@Transactional
public class SubscriptionBillingService {
    private static final Map<String, Integer> PLAN_RANK = Map.of(
            "FREE", 0,
            "STARTER", 1,
            "BUSINESS", 2,
            "PRO", 2);

    private final SubscriptionPlanRepository plans;
    private final OrganizationSubscriptionRepository organizationSubscriptions;
    private final PaymentTransactionRepository paymentTransactions;
    private final SubscriptionInvoiceRepository subscriptionInvoices;
    private final OrganizationMembershipRepository memberships;
    private final SalesInvoiceRepository salesInvoices;
    private final SubscriptionService subscriptionService;
    private final SubscriptionActivationService activationService;
    private final PaymentProviderRegistry paymentProviders;
    private final RazorpayProperties razorpayProperties;
    private final StripeProperties stripeProperties;
    private final CashfreeProperties cashfreeProperties;
    private final PayPalProperties payPalProperties;
    private final ObjectMapper objectMapper;

    public SubscriptionBillingService(
            SubscriptionPlanRepository plans,
            OrganizationSubscriptionRepository organizationSubscriptions,
            PaymentTransactionRepository paymentTransactions,
            SubscriptionInvoiceRepository subscriptionInvoices,
            OrganizationMembershipRepository memberships,
            SalesInvoiceRepository salesInvoices,
            SubscriptionService subscriptionService,
            SubscriptionActivationService activationService,
            PaymentProviderRegistry paymentProviders,
            RazorpayProperties razorpayProperties,
            StripeProperties stripeProperties,
            CashfreeProperties cashfreeProperties,
            PayPalProperties payPalProperties,
            ObjectMapper objectMapper) {
        this.plans = plans;
        this.organizationSubscriptions = organizationSubscriptions;
        this.paymentTransactions = paymentTransactions;
        this.subscriptionInvoices = subscriptionInvoices;
        this.memberships = memberships;
        this.salesInvoices = salesInvoices;
        this.subscriptionService = subscriptionService;
        this.activationService = activationService;
        this.paymentProviders = paymentProviders;
        this.razorpayProperties = razorpayProperties;
        this.stripeProperties = stripeProperties;
        this.cashfreeProperties = cashfreeProperties;
        this.payPalProperties = payPalProperties;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<PlanResponse> listPlans() {
        return plans.findByActiveTrueOrderByDisplayOrderAsc().stream()
                .map(this::toPlanResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CurrentSubscriptionResponse getCurrent(UUID organizationId) {
        OrganizationSubscription orgSub =
                organizationSubscriptions.findByOrganizationId(organizationId).orElse(null);
        SubscriptionPlan plan = subscriptionService.resolvePlanForOrganization(organizationId);
        if (orgSub == null) {
            return new CurrentSubscriptionResponse(
                    toPlanResponse(plan), "NONE", BillingCycle.MONTHLY.name(), null, null, null, true, null, null);
        }
        return toCurrentResponse(orgSub, plan);
    }

    public CheckoutResponse checkout(UUID organizationId, CheckoutRequest request) {
        return startCheckout(organizationId, request, "CHECKOUT", false);
    }

    public CheckoutResponse upgrade(UUID organizationId, CheckoutRequest request) {
        return startCheckout(organizationId, request, "UPGRADE", true);
    }

    public CurrentSubscriptionResponse cancel(UUID organizationId) {
        return cancel(organizationId, false);
    }

    public CurrentSubscriptionResponse cancel(UUID organizationId, boolean immediate) {
        OrganizationSubscription orgSub = organizationSubscriptions
                .findByOrganizationId(organizationId)
                .orElseThrow(() -> new BusinessException("No organization subscription found"));
        SubscriptionPlan currentPlan = subscriptionService.resolvePlanForOrganization(organizationId);
        if ("FREE".equalsIgnoreCase(currentPlan.getCode())) {
            throw new BusinessException("Free plan cannot be cancelled");
        }

        OffsetDateTime now = OffsetDateTime.now();
        orgSub.setAutoRenew(false);

        if (immediate) {
            SubscriptionPlan free =
                    plans.findByCode("FREE").orElseThrow(() -> new BusinessException("FREE plan not found"));
            orgSub.setStatus("CANCELLED");
            orgSub.setEndDate(now);
            orgSub.setNextBillingDate(null);
            orgSub.setPlanId(free.getId());
            organizationSubscriptions.save(orgSub);
            activationService.syncOwningAdminUserSubscription(organizationId, free.getId());
            return toCurrentResponse(orgSub, free);
        }

        OffsetDateTime end = orgSub.getNextBillingDate();
        if (end == null || !end.isAfter(now)) {
            end = "YEARLY".equalsIgnoreCase(orgSub.getBillingCycle()) ? now.plusYears(1) : now.plusMonths(1);
            orgSub.setNextBillingDate(end);
        }
        orgSub.setEndDate(end);
        // Keep ACTIVE so limits apply until period end; autoRenew=false stops renewal.
        if (!"ACTIVE".equalsIgnoreCase(orgSub.getStatus()) && !"TRIAL".equalsIgnoreCase(orgSub.getStatus())) {
            orgSub.setStatus("ACTIVE");
        }
        organizationSubscriptions.save(orgSub);
        return toCurrentResponse(orgSub, currentPlan);
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> listInvoices(UUID organizationId) {
        return subscriptionInvoices.findByOrganizationIdOrderByCreatedAtDesc(organizationId).stream()
                .map(this::toInvoiceResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public UsageResponse usage(UUID userId, UUID organizationId) {
        SubscriptionPlan plan = subscriptionService.resolvePlanForOrganization(organizationId);
        int organizationCount = (int) memberships.findByUserIdAndStatus(userId, "ACTIVE").stream()
                .filter(this::isOrgAdmin)
                .count();
        int userCount = (int) memberships.findByOrganizationId(organizationId).stream()
                .filter(m -> !"INACTIVE".equals(m.getStatus()))
                .count();
        YearMonth month = YearMonth.now();
        int invoiceCount = (int) salesInvoices.countByOrganizationIdAndInvoiceDateBetween(
                organizationId, month.atDay(1), month.atEndOfMonth());
        return new UsageResponse(
                organizationCount,
                plan.getMaxOrganizations(),
                userCount,
                plan.getMaxUsersPerOrg(),
                invoiceCount,
                plan.getMaxInvoicesPerMonth());
    }

    public CurrentSubscriptionResponse verifyPayment(UUID organizationId, VerifyPaymentRequest request) {
        String orderId = request == null ? null : request.resolvedOrderId();
        String paymentId = request == null ? null : request.resolvedPaymentId();
        String signature = request == null ? null : request.resolvedSignature();
        if (orderId == null || paymentId == null) {
            throw new BusinessException("Payment verification details are required");
        }
        PaymentTransaction txn = paymentTransactions
                .findByProviderOrderId(orderId)
                .orElseThrow(() -> new BusinessException("Payment transaction not found"));
        if (!organizationId.equals(txn.getOrganizationId())) {
            throw new BusinessException("Payment transaction does not belong to this organization");
        }
        if ("PAID".equals(txn.getStatus())) {
            return getCurrent(organizationId);
        }

        String providerName = firstNonBlank(
                request.provider(), txn.getProvider(), paymentProviders.active().name());
        PaymentProvider provider = paymentProviders.require(providerName);
        // Razorpay requires signature; other providers may verify via API when signature is blank.
        if ("razorpay".equalsIgnoreCase(provider.name()) && (signature == null || signature.isBlank())) {
            throw new BusinessException("Payment signature is required");
        }
        if (!provider.verifyPayment(orderId, paymentId, signature == null ? "" : signature)) {
            throw new BusinessException("Payment signature verification failed");
        }

        SubscriptionPlan plan =
                plans.findById(txn.getPlanId()).orElseThrow(() -> new BusinessException("Subscription plan not found"));
        BillingCycle cycle = parseCycle(txn.getBillingCycle());
        OrganizationSubscription orgSub =
                activationService.activate(organizationId, plan, cycle, txn, provider.name(), paymentId);
        return toCurrentResponse(orgSub, plan);
    }

    public static BigDecimal priceForCycle(SubscriptionPlan plan, BillingCycle cycle) {
        if (cycle == BillingCycle.YEARLY) {
            return plan.getPriceYearly() == null ? BigDecimal.ZERO : plan.getPriceYearly();
        }
        return plan.getPriceMonthly() == null ? BigDecimal.ZERO : plan.getPriceMonthly();
    }

    private CheckoutResponse startCheckout(
            UUID organizationId, CheckoutRequest request, String purpose, boolean upgrade) {
        if (request == null || request.planCode() == null || request.planCode().isBlank()) {
            throw new BusinessException("Plan code is required");
        }
        BillingCycle cycle = request.billingCycle() == null ? BillingCycle.MONTHLY : request.billingCycle();
        String planCode = normalizePlanCode(request.planCode());
        SubscriptionPlan target = plans.findByCode(planCode)
                .orElseThrow(() -> new BusinessException("Subscription plan not found: " + request.planCode()));
        if (!target.isActive()) {
            throw new BusinessException("Subscription plan is inactive");
        }

        OrganizationSubscription current =
                organizationSubscriptions.findByOrganizationId(organizationId).orElse(null);
        SubscriptionPlan currentPlan = subscriptionService.resolvePlanForOrganization(organizationId);

        if (current != null
                && currentPlan.getCode().equalsIgnoreCase(target.getCode())
                && cycle.name().equals(current.getBillingCycle())
                && ("ACTIVE".equals(current.getStatus()) || "TRIAL".equals(current.getStatus()))) {
            throw new BusinessException("Already subscribed to this plan and billing cycle");
        }

        if (upgrade) {
            assertUpgradeAllowed(currentPlan, target);
        }

        BigDecimal amount = priceForCycle(target, cycle);
        if (amount.compareTo(BigDecimal.ZERO) <= 0 || "FREE".equalsIgnoreCase(target.getCode())) {
            OrganizationSubscription orgSub =
                    activationService.activate(organizationId, target, cycle, null, null, null);
            return new CheckoutResponse(
                    true,
                    null,
                    null,
                    null,
                    BigDecimal.ZERO,
                    target.getCurrency(),
                    target.getCode(),
                    cycle.name(),
                    null,
                    toCurrentResponse(orgSub, target),
                    null,
                    null);
        }

        PaymentProvider provider = paymentProviders.active();
        PaymentTransaction txn = new PaymentTransaction();
        txn.setOrganizationId(organizationId);
        txn.setProvider(provider.name());
        txn.setAmount(amount);
        txn.setCurrency(target.getCurrency() == null ? "INR" : target.getCurrency());
        txn.setStatus("CREATED");
        txn.setPurpose(purpose);
        txn.setPlanId(target.getId());
        txn.setBillingCycle(cycle.name());
        txn = paymentTransactions.save(txn);

        var order = provider.createOrder(new CreateOrderRequest(
                amount,
                txn.getCurrency(),
                "sub-" + organizationId.toString().substring(0, 8) + "-" + LocalDate.now(),
                purpose + ":" + target.getCode()));
        txn.setProviderOrderId(order.orderId());
        txn.setStatus("PENDING");
        try {
            txn.setRawResponse(objectMapper.readTree(order.rawJson() == null ? "{}" : order.rawJson()));
        } catch (Exception ignored) {
            // raw response is optional
        }
        txn = paymentTransactions.save(txn);

        String clientSecret = null;
        String checkoutUrl = null;
        try {
            if (order.rawJson() != null) {
                var raw = objectMapper.readTree(order.rawJson());
                if (raw.hasNonNull("client_secret")) {
                    clientSecret = raw.path("client_secret").asText(null);
                }
                if (raw.has("links") && raw.path("links").isArray()) {
                    for (var link : raw.path("links")) {
                        if ("approve".equalsIgnoreCase(link.path("rel").asText())) {
                            checkoutUrl = link.path("href").asText(null);
                            break;
                        }
                    }
                }
                if (raw.hasNonNull("payment_session_id")) {
                    clientSecret = raw.path("payment_session_id").asText(clientSecret);
                }
            }
        } catch (Exception ignored) {
            // optional client fields
        }

        return new CheckoutResponse(
                false,
                provider.name(),
                clientKeyFor(provider.name()),
                order.orderId(),
                amount,
                txn.getCurrency(),
                target.getCode(),
                cycle.name(),
                txn.getId(),
                null,
                clientSecret,
                checkoutUrl);
    }

    private String clientKeyFor(String provider) {
        if (provider == null) {
            return null;
        }
        return switch (provider.toLowerCase(Locale.ROOT)) {
            case "razorpay" -> razorpayProperties.getKeyId();
            case "stripe" -> stripeProperties.getPublishableKey();
            case "cashfree" -> cashfreeProperties.getAppId();
            case "paypal" -> payPalProperties.getClientId();
            default -> null;
        };
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String normalizePlanCode(String code) {
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        if ("PRO".equals(normalized)) {
            return "BUSINESS";
        }
        return normalized;
    }

    private void assertUpgradeAllowed(SubscriptionPlan current, SubscriptionPlan target) {
        int from = PLAN_RANK.getOrDefault(current.getCode().toUpperCase(Locale.ROOT), 0);
        int to = PLAN_RANK.getOrDefault(target.getCode().toUpperCase(Locale.ROOT), 0);
        if (to < from) {
            throw new BusinessException("Downgrade is not supported via upgrade; cancel or wait for period end");
        }
        if (to == from) {
            throw new BusinessException("Use checkout to change billing cycle for the same plan");
        }
    }

    private CurrentSubscriptionResponse toCurrentResponse(OrganizationSubscription orgSub, SubscriptionPlan plan) {
        return new CurrentSubscriptionResponse(
                toPlanResponse(plan),
                orgSub.getStatus(),
                orgSub.getBillingCycle(),
                orgSub.getStartDate(),
                orgSub.getEndDate(),
                orgSub.getNextBillingDate(),
                orgSub.isAutoRenew(),
                orgSub.getPaymentProvider(),
                orgSub.getPaymentReference());
    }

    private PlanResponse toPlanResponse(SubscriptionPlan plan) {
        return new PlanResponse(
                plan.getId(),
                plan.getCode(),
                plan.getName(),
                plan.getDescription(),
                plan.getMaxOrganizations(),
                plan.getMaxUsersPerOrg(),
                plan.getMaxInvoicesPerMonth(),
                plan.getPriceMonthly(),
                plan.getPriceYearly(),
                plan.getCurrency(),
                plan.getDisplayOrder(),
                plan.isHighlightPlan(),
                plan.isRecommended(),
                plan.getTrialDays());
    }

    private InvoiceResponse toInvoiceResponse(SubscriptionInvoice invoice) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getAmount(),
                invoice.getGst(),
                invoice.getDiscount(),
                invoice.getTotal(),
                invoice.getPaidAt(),
                invoice.getPdfUrl(),
                invoice.getCreatedAt());
    }

    private BillingCycle parseCycle(String value) {
        try {
            return BillingCycle.valueOf(value == null ? "MONTHLY" : value.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            return BillingCycle.MONTHLY;
        }
    }

    private boolean isOrgAdmin(OrganizationMembership membership) {
        return membership.getRoles().stream().map(Role::getCode).anyMatch("ORGANIZATION_ADMIN"::equals);
    }
}
