package com.flowledger.commerce.payment;

import com.flowledger.commerce.checkout.entity.CommerceCheckoutSession;
import com.flowledger.commerce.common.CommerceTenantScope;
import com.flowledger.commerce.payment.domain.CommercePaymentProvider;
import com.flowledger.commerce.payment.domain.PaymentSessionStatus;
import com.flowledger.commerce.payment.entity.CommercePaymentSession;
import com.flowledger.commerce.payment.repository.CommercePaymentSessionRepository;
import com.flowledger.payment.dto.PaymentDtos;
import com.flowledger.payment.entity.Payment;
import com.flowledger.payment.service.PaymentService;
import com.flowledger.subscription.integration.PaymentProvider;
import com.flowledger.subscription.integration.PaymentProviderRegistry;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PaymentOrchestrator {
    private final CommercePaymentSessionRepository sessions;
    private final PaymentProviderRegistry paymentProviders;
    private final PaymentService erpPayments;

    public PaymentOrchestrator(
            CommercePaymentSessionRepository sessions,
            PaymentProviderRegistry paymentProviders,
            PaymentService erpPayments) {
        this.sessions = sessions;
        this.paymentProviders = paymentProviders;
        this.erpPayments = erpPayments;
    }

    public CommercePaymentSession initiate(CommerceCheckoutSession checkout, CommercePaymentProvider provider) {
        String key = "commerce:" + checkout.getId() + ":" + provider.name();
        return sessions.findByIdempotencyKey(key).orElseGet(() -> {
            CommercePaymentSession session = new CommercePaymentSession();
            session.setCheckoutSessionId(checkout.getId());
            session.setProvider(provider);
            session.setAmount(checkout.getGrandTotal());
            session.setCurrency(checkout.getCurrency());
            session.setIdempotencyKey(key);
            session.setStatus(PaymentSessionStatus.PENDING);

            if (provider == CommercePaymentProvider.COD || provider == CommercePaymentProvider.CASH) {
                sessions.save(session);
                return session;
            }

            PaymentProvider gateway = paymentProviders.require(provider.name().toLowerCase());
            PaymentProvider.CreateOrderResult result = gateway.createOrder(new PaymentProvider.CreateOrderRequest(
                    checkout.getGrandTotal(),
                    checkout.getCurrency(),
                    key,
                    "{\"checkoutSessionId\":\"" + checkout.getId() + "\"}"));
            session.setGatewayOrderId(result.orderId());
            session.setRawResponse(result.rawJson());
            return sessions.save(session);
        });
    }

    public CommercePaymentSession markPaid(
            CommercePaymentSession session, String gatewayPaymentId, String signature, UUID erpInvoiceId) {
        if (session.getProvider() != CommercePaymentProvider.COD
                && session.getProvider() != CommercePaymentProvider.CASH) {
            PaymentProvider gateway = paymentProviders.require(session.getProvider().name().toLowerCase());
            if (!gateway.verifyPayment(session.getGatewayOrderId(), gatewayPaymentId, signature)) {
                session.setStatus(PaymentSessionStatus.FAILED);
                return sessions.save(session);
            }
        }
        session.setGatewayPaymentId(gatewayPaymentId);
        session.setStatus(PaymentSessionStatus.PAID);
        session.setPaidAt(OffsetDateTime.now());
        sessions.save(session);

        if (erpInvoiceId != null) {
            // ERP payment recording handled in checkout confirm with org context
        }
        return session;
    }

    public void recordErpReceipt(UUID organizationId, UUID erpCustomerId, UUID erpInvoiceId, CommercePaymentSession session) {
        CommerceTenantScope.runVoid(organizationId, () -> {
            PaymentDtos.PaymentRequest request = new PaymentDtos.PaymentRequest(
                    java.time.LocalDate.now(),
                    Payment.Type.RECEIPT,
                    Payment.Party.CUSTOMER,
                    erpCustomerId,
                    null,
                    session.getAmount(),
                    session.getProvider().name(),
                    session.getGatewayPaymentId(),
                    null,
                    "Commerce payment " + session.getId(),
                    java.util.List.of(new PaymentDtos.Allocation("SALES_INVOICE", erpInvoiceId, session.getAmount())));
            erpPayments.create(request);
        });
    }
}
