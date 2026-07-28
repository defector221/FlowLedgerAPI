package com.flowledger.commerce.checkout;

import com.flowledger.commerce.auth.CommerceSecurityContext;
import com.flowledger.commerce.cart.domain.CartStatus;
import com.flowledger.commerce.cart.entity.CommerceCart;
import com.flowledger.commerce.cart.entity.CommerceCartItem;
import com.flowledger.commerce.cart.repository.CommerceCartItemRepository;
import com.flowledger.commerce.cart.repository.CommerceCartRepository;
import com.flowledger.commerce.cart.CartMapper;
import com.flowledger.commerce.checkout.domain.CheckoutSessionStatus;
import com.flowledger.commerce.checkout.entity.CommerceCheckoutSession;
import com.flowledger.commerce.checkout.repository.CommerceCheckoutSessionRepository;
import com.flowledger.commerce.config.CommerceProperties;
import com.flowledger.commerce.customer.entity.CommerceCustomerAddress;
import com.flowledger.commerce.customer.repository.CommerceCustomerAddressRepository;
import com.flowledger.commerce.dto.CommerceDtos;
import com.flowledger.commerce.fulfillment.FulfillmentType;
import com.flowledger.commerce.order.OrderOrchestrator;
import com.flowledger.commerce.order.entity.CommerceOrder;
import com.flowledger.commerce.order.OrderMapper;
import com.flowledger.commerce.payment.PaymentOrchestrator;
import com.flowledger.commerce.payment.domain.CommercePaymentProvider;
import com.flowledger.commerce.payment.domain.PaymentSessionStatus;
import com.flowledger.commerce.payment.entity.CommercePaymentSession;
import com.flowledger.commerce.payment.repository.CommercePaymentSessionRepository;
import com.flowledger.commerce.events.CommerceOrderPlacedEvent;
import com.flowledger.platform.event.DomainEventPublisher;
import com.flowledger.commerce.pricing.CommercePricingService;
import com.flowledger.commerce.store.entity.StoreCommerceProfile;
import com.flowledger.commerce.store.repository.StoreCommerceProfileRepository;
import com.flowledger.commerce.validation.CartValidationService;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.retail.dto.RetailDtos.ApplyCouponResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CheckoutService {
    private final CommerceCartRepository carts;
    private final CommerceCartItemRepository cartItems;
    private final CommerceCheckoutSessionRepository sessions;
    private final StoreCommerceProfileRepository storeProfiles;
    private final CommerceCustomerAddressRepository addresses;
    private final CartValidationService validation;
    private final CommercePricingService pricing;
    private final CommerceProperties properties;
    private final CartMapper cartMapper;
    private final PaymentOrchestrator paymentOrchestrator;
    private final CommercePaymentSessionRepository paymentSessions;
    private final OrderOrchestrator orderOrchestrator;
    private final OrderMapper orderMapper;
    private final DomainEventPublisher events;

    public CheckoutService(
            CommerceCartRepository carts,
            CommerceCartItemRepository cartItems,
            CommerceCheckoutSessionRepository sessions,
            StoreCommerceProfileRepository storeProfiles,
            CommerceCustomerAddressRepository addresses,
            CartValidationService validation,
            CommercePricingService pricing,
            CommerceProperties properties,
            CartMapper cartMapper,
            PaymentOrchestrator paymentOrchestrator,
            CommercePaymentSessionRepository paymentSessions,
            OrderOrchestrator orderOrchestrator,
            OrderMapper orderMapper,
            DomainEventPublisher events) {
        this.carts = carts;
        this.cartItems = cartItems;
        this.sessions = sessions;
        this.storeProfiles = storeProfiles;
        this.addresses = addresses;
        this.validation = validation;
        this.pricing = pricing;
        this.properties = properties;
        this.cartMapper = cartMapper;
        this.paymentOrchestrator = paymentOrchestrator;
        this.paymentSessions = paymentSessions;
        this.orderOrchestrator = orderOrchestrator;
        this.orderMapper = orderMapper;
        this.events = events;
    }

    public CommerceDtos.CheckoutSessionResponse startCheckout(CommerceDtos.StartCheckoutRequest request) {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        CommerceCart cart = carts.findByIdAndCustomerId(request.cartId(), customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));
        if (cart.getStatus() != CartStatus.ACTIVE) {
            throw new BusinessException("Cart is not active");
        }

        List<CommerceCartItem> items = cartItems.findByCartIdOrderByCreatedAtAsc(cart.getId());
        CartValidationService.CartValidationResult result = validation.validate(cart, items);
        if (!result.valid()) {
            throw new BusinessException(String.join("; ", result.errors()));
        }

        StoreCommerceProfile profile = storeProfiles
                .findByStoreId(cart.getStoreId())
                .orElseThrow(() -> new ResourceNotFoundException("Store profile not found"));
        profile.assertCanAcceptDigitalOrder(request.fulfillmentType());

        if (request.fulfillmentType() == FulfillmentType.HOME_DELIVERY && request.addressId() == null) {
            throw new BusinessException("Delivery address is required");
        }
        if (request.addressId() != null) {
            CommerceCustomerAddress address = addresses
                    .findByIdAndCustomerId(request.addressId(), customerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Address not found"));
            if (request.fulfillmentType() == FulfillmentType.HOME_DELIVERY && address.getState() == null) {
                throw new BusinessException("Address state is required for tax calculation");
            }
        }

        BigDecimal discount = BigDecimal.ZERO;
        String coupon = request.couponCode();
        if (coupon != null && !coupon.isBlank()) {
            ApplyCouponResponse couponResult =
                    pricing.applyCoupon(cart.getOrganizationId(), coupon, cart.getGrandTotal());
            if (couponResult.applied()) {
                discount = couponResult.discountAmount();
            }
        }

        CommerceCheckoutSession session = new CommerceCheckoutSession();
        session.setCartId(cart.getId());
        session.setCustomerId(customerId);
        session.setOrganizationId(cart.getOrganizationId());
        session.setStoreId(cart.getStoreId());
        session.setFulfillmentType(request.fulfillmentType());
        session.setAddressId(request.addressId());
        session.setCouponCode(coupon);
        session.setCurrency(cart.getCurrency());
        session.setSubtotal(cart.getSubtotal());
        session.setDiscountTotal(discount);
        session.setTaxTotal(cart.getTaxTotal());
        session.setShippingTotal(BigDecimal.ZERO);
        session.setGrandTotal(cart.getGrandTotal().subtract(discount));
        session.setExpiresAt(OffsetDateTime.now().plusMinutes(properties.getCart().getCheckoutTtlMinutes()));
        session.setStatus(CheckoutSessionStatus.OPEN);
        session = sessions.save(session);
        return toSessionResponse(session, items);
    }

    public CommerceDtos.CheckoutSessionResponse getSession(UUID sessionId) {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        CommerceCheckoutSession session = sessions.findByIdAndCustomerId(sessionId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Checkout session not found"));
        List<CommerceCartItem> items = cartItems.findByCartIdOrderByCreatedAtAsc(session.getCartId());
        return toSessionResponse(session, items);
    }

    public CommerceDtos.CheckoutSessionResponse applyCoupon(UUID sessionId, CommerceDtos.ApplyCheckoutCouponRequest request) {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        CommerceCheckoutSession session = sessions.findByIdAndCustomerId(sessionId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Checkout session not found"));
        if (session.getStatus() != CheckoutSessionStatus.OPEN) {
            throw new BusinessException("Cannot apply coupon to this checkout session");
        }

        ApplyCouponResponse couponResult =
                pricing.applyCoupon(session.getOrganizationId(), request.couponCode(), session.getSubtotal());
        if (!couponResult.applied()) {
            throw new BusinessException("Coupon not applicable");
        }

        session.setCouponCode(request.couponCode());
        session.setDiscountTotal(couponResult.discountAmount());
        session.setGrandTotal(session.getSubtotal().add(session.getTaxTotal()).subtract(couponResult.discountAmount()));
        sessions.save(session);

        List<CommerceCartItem> items = cartItems.findByCartIdOrderByCreatedAtAsc(session.getCartId());
        return toSessionResponse(session, items);
    }

    public CommerceDtos.PaymentSessionResponse initiatePayment(UUID sessionId, CommerceDtos.InitiatePaymentRequest request) {
        CommerceCheckoutSession session = loadOpenSession(sessionId);
        session.setStatus(CheckoutSessionStatus.PAYMENT_PENDING);
        sessions.save(session);
        CommercePaymentSession payment = paymentOrchestrator.initiate(session, request.provider());
        return new CommerceDtos.PaymentSessionResponse(
                payment.getId(),
                payment.getCheckoutSessionId(),
                payment.getProvider().name(),
                payment.getStatus().name(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getGatewayOrderId());
    }

    public CommerceDtos.CommerceOrderResponse confirmCheckout(UUID sessionId, CommerceDtos.ConfirmCheckoutRequest request) {
        CommerceCheckoutSession session = loadConfirmableSession(sessionId);
        CommercePaymentSession payment = resolvePaymentForConfirm(session, request);
        return completePaidCheckout(session.getId(), payment);
    }

    public CommerceDtos.CommerceOrderResponse completePaidCheckout(UUID sessionId, CommercePaymentSession payment) {
        CommerceCheckoutSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Checkout session not found"));
        if (session.getStatus() == CheckoutSessionStatus.COMPLETED) {
            return orderOrchestrator
                    .findByCheckoutSession(session.getId())
                    .map(orderMapper::toOrderResponse)
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        }

        if (payment.getStatus() != PaymentSessionStatus.PAID) {
            throw new BusinessException("Payment not completed");
        }

        CommerceOrder order = orderOrchestrator.placeOrder(session);
        if (order.getErpInvoiceId() != null) {
            paymentOrchestrator.recordErpReceipt(
                    session.getOrganizationId(), order.getErpCustomerId(), order.getErpInvoiceId(), payment);
        }

        session.setStatus(CheckoutSessionStatus.COMPLETED);
        session.setCompletedAt(OffsetDateTime.now());
        sessions.save(session);

        events.publish(new CommerceOrderPlacedEvent(
                this, session.getOrganizationId(), session.getCustomerId(), order.getId()));
        return orderMapper.toOrderResponse(order);
    }

    private CommercePaymentSession resolvePaymentForConfirm(
            CommerceCheckoutSession session, CommerceDtos.ConfirmCheckoutRequest request) {
        Optional<CommercePaymentSession> existing =
                paymentSessions.findFirstByCheckoutSessionIdOrderByCreatedAtDesc(session.getId());
        if (existing.isPresent()) {
            CommercePaymentSession payment = existing.get();
            if (payment.getStatus() == PaymentSessionStatus.PAID) {
                return payment;
            }
            paymentOrchestrator.markPaid(
                    payment,
                    request != null ? request.gatewayPaymentId() : null,
                    request != null ? request.gatewaySignature() : null,
                    null);
            if (payment.getStatus() != PaymentSessionStatus.PAID) {
                throw new BusinessException("Payment verification failed");
            }
            return payment;
        }

        CommercePaymentSession payment = paymentOrchestrator.initiate(session, CommercePaymentProvider.COD);
        paymentOrchestrator.markPaid(
                payment,
                request != null ? request.gatewayPaymentId() : null,
                request != null ? request.gatewaySignature() : null,
                null);
        if (payment.getStatus() != PaymentSessionStatus.PAID) {
            throw new BusinessException("Payment verification failed");
        }
        return payment;
    }

    private CommerceCheckoutSession loadConfirmableSession(UUID sessionId) {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        CommerceCheckoutSession session = sessions.findByIdAndCustomerId(sessionId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Checkout session not found"));
        if (session.getStatus() != CheckoutSessionStatus.OPEN
                && session.getStatus() != CheckoutSessionStatus.PAYMENT_PENDING) {
            throw new BusinessException("Checkout session is not confirmable");
        }
        if (session.getExpiresAt().isBefore(OffsetDateTime.now())) {
            session.setStatus(CheckoutSessionStatus.EXPIRED);
            sessions.save(session);
            throw new BusinessException("Checkout session expired");
        }
        return session;
    }

    private CommerceCheckoutSession loadOpenSession(UUID sessionId) {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        CommerceCheckoutSession session = sessions.findByIdAndCustomerId(sessionId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Checkout session not found"));
        if (session.getStatus() != CheckoutSessionStatus.OPEN
                && session.getStatus() != CheckoutSessionStatus.PAYMENT_PENDING) {
            throw new BusinessException("Checkout session is not open");
        }
        if (session.getExpiresAt().isBefore(OffsetDateTime.now())) {
            session.setStatus(CheckoutSessionStatus.EXPIRED);
            sessions.save(session);
            throw new BusinessException("Checkout session expired");
        }
        return session;
    }

    private CommerceDtos.CheckoutSessionResponse toSessionResponse(
            CommerceCheckoutSession session, List<CommerceCartItem> items) {
        return new CommerceDtos.CheckoutSessionResponse(
                session.getId(),
                session.getCartId(),
                session.getStoreId(),
                session.getStatus().name(),
                session.getFulfillmentType().name(),
                session.getAddressId(),
                session.getCouponCode(),
                session.getCurrency(),
                session.getSubtotal(),
                session.getDiscountTotal(),
                session.getTaxTotal(),
                session.getShippingTotal(),
                session.getGrandTotal(),
                session.getExpiresAt(),
                items.stream().map(cartMapper::toItemResponse).toList());
    }
}
