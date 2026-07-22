package com.flowledger.subscription.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.auth.repository.OrganizationMembershipRepository;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.util.DocumentNumberService;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.sales.repository.SalesInvoiceRepository;
import com.flowledger.subscription.config.RazorpayProperties;
import com.flowledger.subscription.domain.BillingCycle;
import com.flowledger.subscription.dto.SubscriptionDtos.CheckoutRequest;
import com.flowledger.subscription.dto.SubscriptionDtos.CheckoutResponse;
import com.flowledger.subscription.entity.OrganizationSubscription;
import com.flowledger.subscription.entity.PaymentTransaction;
import com.flowledger.subscription.entity.PaymentWebhookEvent;
import com.flowledger.subscription.entity.SubscriptionPlan;
import com.flowledger.subscription.integration.PaymentProvider;
import com.flowledger.subscription.integration.PaymentProviderRegistry;
import com.flowledger.subscription.integration.RazorpayProvider;
import com.flowledger.subscription.repository.OrganizationSubscriptionRepository;
import com.flowledger.subscription.repository.PaymentTransactionRepository;
import com.flowledger.subscription.repository.PaymentWebhookEventRepository;
import com.flowledger.subscription.repository.SubscriptionInvoiceRepository;
import com.flowledger.subscription.repository.SubscriptionPlanRepository;
import com.flowledger.subscription.repository.UserSubscriptionRepository;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubscriptionBillingServiceTest {
    @Mock
    SubscriptionPlanRepository plans;

    @Mock
    OrganizationSubscriptionRepository organizationSubscriptions;

    @Mock
    PaymentTransactionRepository paymentTransactions;

    @Mock
    SubscriptionInvoiceRepository subscriptionInvoices;

    @Mock
    OrganizationMembershipRepository memberships;

    @Mock
    SalesInvoiceRepository salesInvoices;

    @Mock
    UserSubscriptionRepository userSubscriptions;

    @Mock
    OrganizationRepository organizations;

    @Mock
    DocumentNumberService documentNumbers;

    @Mock
    PaymentProviderRegistry paymentProviders;

    @Mock
    PaymentProvider paymentProvider;

    RazorpayProperties razorpayProperties = new RazorpayProperties();
    ObjectMapper objectMapper = new ObjectMapper();

    SubscriptionActivationService activationService;
    SubscriptionService subscriptionService;
    SubscriptionBillingService billingService;
    SubscriptionWebhookService webhookService;

    @Mock
    PaymentWebhookEventRepository webhookEvents;

    UUID orgId = UUID.randomUUID();
    SubscriptionPlan free;
    SubscriptionPlan starter;

    @BeforeEach
    void setUp() {
        activationService = new SubscriptionActivationService(
                organizationSubscriptions,
                userSubscriptions,
                memberships,
                paymentTransactions,
                subscriptionInvoices,
                organizations,
                documentNumbers);
        subscriptionService = new SubscriptionService(
                userSubscriptions, plans, memberships, organizationSubscriptions, salesInvoices);
        billingService = new SubscriptionBillingService(
                plans,
                organizationSubscriptions,
                paymentTransactions,
                subscriptionInvoices,
                memberships,
                salesInvoices,
                subscriptionService,
                activationService,
                paymentProviders,
                razorpayProperties,
                new com.flowledger.subscription.config.StripeProperties(),
                new com.flowledger.subscription.config.CashfreeProperties(),
                new com.flowledger.subscription.config.PayPalProperties(),
                objectMapper);
        webhookService = new SubscriptionWebhookService(
                webhookEvents, paymentTransactions, plans, activationService, paymentProviders, objectMapper);

        free = plan("FREE", "Free", BigDecimal.ZERO, BigDecimal.ZERO);
        starter = plan("STARTER", "Starter", new BigDecimal("499"), new BigDecimal("4990"));
    }

    @Test
    void freeCheckoutActivatesWithoutGateway() {
        when(plans.findByCode("FREE")).thenReturn(Optional.of(free));
        when(organizationSubscriptions.findByOrganizationId(orgId)).thenReturn(Optional.empty());
        when(plans.findByCode("FREE")).thenReturn(Optional.of(free));
        when(memberships.findByOrganizationId(orgId)).thenReturn(List.of());
        when(organizationSubscriptions.save(any(OrganizationSubscription.class))).thenAnswer(inv -> {
            OrganizationSubscription s = inv.getArgument(0);
            if (s.getId() == null) {
                s.setId(UUID.randomUUID());
            }
            return s;
        });
        Organization org = new Organization();
        org.setFinancialYearStart("04-01");
        when(organizations.findById(orgId)).thenReturn(Optional.of(org));
        when(documentNumbers.next(eq(orgId), eq("SUBSCRIPTION_INVOICE"), eq("SUB"), any(), any(), any()))
                .thenReturn("SUB/2025-26/000001");
        when(subscriptionInvoices.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CheckoutResponse response =
                billingService.checkout(orgId, new CheckoutRequest("FREE", BillingCycle.MONTHLY));

        assertTrue(response.activated());
        assertNull(response.orderId());
        assertNotNull(response.subscription());
        assertEquals("FREE", response.subscription().plan().code());
        verify(paymentProviders, never()).active();
        verify(paymentTransactions, never()).save(any());
    }

    @Test
    void priceForCycleUsesYearlyWhenRequested() {
        assertEquals(0, SubscriptionBillingService.priceForCycle(starter, BillingCycle.MONTHLY)
                .compareTo(new BigDecimal("499")));
        assertEquals(0, SubscriptionBillingService.priceForCycle(starter, BillingCycle.YEARLY)
                .compareTo(new BigDecimal("4990")));
    }

    @Test
    void invoiceLimitThrowsWhenExceeded() {
        when(organizationSubscriptions.findByOrganizationId(orgId)).thenReturn(Optional.empty());
        when(memberships.findByOrganizationId(orgId)).thenReturn(List.of());
        when(plans.findByCode("FREE")).thenReturn(Optional.of(free));
        free.setMaxInvoicesPerMonth(2);
        YearMonth month = YearMonth.now();
        when(salesInvoices.countByOrganizationIdAndInvoiceDateBetween(
                        orgId, month.atDay(1), month.atEndOfMonth()))
                .thenReturn(2L);

        BusinessException ex =
                assertThrows(BusinessException.class, () -> subscriptionService.checkCanCreateInvoice(orgId));
        assertTrue(ex.getMessage().contains("Invoice limit"));
    }

    @Test
    void webhookDuplicateIsIgnored() {
        PaymentWebhookEvent existing = new PaymentWebhookEvent();
        existing.setEventId("evt_1");
        existing.setProcessed(true);
        when(webhookEvents.findByEventId("evt_1")).thenReturn(Optional.of(existing));
        when(paymentProviders.require("razorpay")).thenReturn(new RazorpayProvider(razorpayProperties, objectMapper));

        String payload = "{\"event_id\":\"evt_1\",\"event\":\"payment.captured\",\"payload\":{}}";
        webhookService.handleRazorpay(payload, "any");

        verify(paymentTransactions, never()).findByProviderOrderId(any());
        verify(webhookEvents, never()).save(any());
    }

    @Test
    void paidCheckoutCreatesProviderOrder() throws Exception {
        when(plans.findByCode("STARTER")).thenReturn(Optional.of(starter));
        when(organizationSubscriptions.findByOrganizationId(orgId)).thenReturn(Optional.empty());
        when(memberships.findByOrganizationId(orgId)).thenReturn(List.of());
        when(plans.findByCode("FREE")).thenReturn(Optional.of(free));
        when(paymentProviders.active()).thenReturn(paymentProvider);
        when(paymentProvider.name()).thenReturn("razorpay");
        when(paymentProvider.createOrder(any())).thenReturn(new PaymentProvider.CreateOrderResult(
                "order_abc", new BigDecimal("499"), "INR", "{\"id\":\"order_abc\"}"));
        when(paymentTransactions.save(any(PaymentTransaction.class))).thenAnswer(inv -> {
            PaymentTransaction t = inv.getArgument(0);
            if (t.getId() == null) {
                t.setId(UUID.randomUUID());
            }
            return t;
        });
        razorpayProperties.setKeyId("rzp_test_key");

        CheckoutResponse response =
                billingService.checkout(orgId, new CheckoutRequest("STARTER", BillingCycle.MONTHLY));

        assertEquals(false, response.activated());
        assertEquals("order_abc", response.orderId());
        assertEquals("rzp_test_key", response.keyId());
        ArgumentCaptor<PaymentTransaction> captor = ArgumentCaptor.forClass(PaymentTransaction.class);
        verify(paymentTransactions, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
        assertEquals("PENDING", captor.getValue().getStatus());
        assertEquals("order_abc", captor.getValue().getProviderOrderId());
    }

    private static SubscriptionPlan plan(String code, String name, BigDecimal monthly, BigDecimal yearly) {
        SubscriptionPlan p = new SubscriptionPlan();
        p.setId(UUID.randomUUID());
        p.setCode(code);
        p.setName(name);
        p.setPriceMonthly(monthly);
        p.setPriceYearly(yearly);
        p.setCurrency("INR");
        p.setActive(true);
        p.setMaxInvoicesPerMonth(25);
        p.setMaxOrganizations(1);
        p.setMaxUsersPerOrg(2);
        return p;
    }
}
