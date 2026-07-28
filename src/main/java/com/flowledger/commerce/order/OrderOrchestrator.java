package com.flowledger.commerce.order;

import com.flowledger.commerce.cart.domain.CartStatus;
import com.flowledger.commerce.cart.repository.CommerceCartRepository;
import com.flowledger.commerce.checkout.entity.CommerceCheckoutSession;
import com.flowledger.commerce.checkout.entity.CommerceCouponRedemption;
import com.flowledger.commerce.checkout.repository.CommerceCouponRedemptionRepository;
import com.flowledger.commerce.customer.bridge.CommerceCustomerBridgeService;
import com.flowledger.commerce.fulfillment.engine.FulfillmentOrchestrator;
import com.flowledger.commerce.order.domain.CommerceOrderStatus;
import com.flowledger.commerce.order.entity.CommerceOrder;
import com.flowledger.commerce.order.entity.CommerceOrderLine;
import com.flowledger.commerce.order.repository.CommerceOrderLineRepository;
import com.flowledger.commerce.order.repository.CommerceOrderRepository;
import com.flowledger.commerce.cart.entity.CommerceCartItem;
import com.flowledger.commerce.cart.repository.CommerceCartItemRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OrderOrchestrator {
    private final CommerceOrderRepository orders;
    private final CommerceOrderLineRepository orderLines;
    private final CommerceCartRepository carts;
    private final CommerceCartItemRepository cartItems;
    private final CommerceCustomerBridgeService customerBridge;
    private final FulfillmentOrchestrator fulfillmentOrchestrator;
    private final CommerceCouponRedemptionRepository couponRedemptions;

    public OrderOrchestrator(
            CommerceOrderRepository orders,
            CommerceOrderLineRepository orderLines,
            CommerceCartRepository carts,
            CommerceCartItemRepository cartItems,
            CommerceCustomerBridgeService customerBridge,
            FulfillmentOrchestrator fulfillmentOrchestrator,
            CommerceCouponRedemptionRepository couponRedemptions) {
        this.orders = orders;
        this.orderLines = orderLines;
        this.carts = carts;
        this.cartItems = cartItems;
        this.customerBridge = customerBridge;
        this.fulfillmentOrchestrator = fulfillmentOrchestrator;
        this.couponRedemptions = couponRedemptions;
    }

    public CommerceOrder placeOrder(CommerceCheckoutSession session) {
        orders.findByCheckoutSessionId(session.getId()).ifPresent(o -> {
            throw new IllegalStateException("Order already placed for checkout session");
        });

        UUID erpCustomerId = customerBridge.findOrCreateErpCustomer(session.getOrganizationId(), session.getCustomerId());

        CommerceOrder order = new CommerceOrder();
        order.setCheckoutSessionId(session.getId());
        order.setCustomerId(session.getCustomerId());
        order.setOrganizationId(session.getOrganizationId());
        order.setStoreId(session.getStoreId());
        order.setErpCustomerId(erpCustomerId);
        order.setFulfillmentType(session.getFulfillmentType());
        order.setAddressId(session.getAddressId());
        order.setCurrency(session.getCurrency());
        order.setSubtotal(session.getSubtotal());
        order.setDiscountTotal(session.getDiscountTotal());
        order.setTaxTotal(session.getTaxTotal());
        order.setShippingTotal(session.getShippingTotal());
        order.setGrandTotal(session.getGrandTotal());
        order.setOrderNumber(generateOrderNumber(session.getOrganizationId()));
        order.setStatus(CommerceOrderStatus.PLACED);
        order.setConfirmedAt(OffsetDateTime.now());
        order = orders.save(order);

        List<CommerceCartItem> items = cartItems.findByCartIdOrderByCreatedAtAsc(session.getCartId());
        for (CommerceCartItem item : items) {
            CommerceOrderLine line = new CommerceOrderLine();
            line.setOrderId(order.getId());
            line.setProductId(item.getProductId());
            line.setVariantId(item.getVariantId());
            line.setQuantity(item.getQuantity());
            line.setLineSubtotal(item.getLineSubtotal());
            line.setLineTax(item.getLineTax());
            line.setLineTotal(item.getLineTotal());
            line.setProductSnapshot(item.getProductSnapshot());
            line.setPriceSnapshot(item.getPriceSnapshot());
            line.setTaxSnapshot(item.getTaxSnapshot());
            line.setPromotionSnapshot(item.getPromotionSnapshot());
            orderLines.save(line);
        }

        if (session.getCouponCode() != null && !session.getCouponCode().isBlank()) {
            CommerceCouponRedemption redemption = new CommerceCouponRedemption();
            redemption.setCheckoutSessionId(session.getId());
            redemption.setOrderId(order.getId());
            redemption.setCouponCode(session.getCouponCode());
            redemption.setDiscountApplied(session.getDiscountTotal());
            couponRedemptions.save(redemption);
        }

        carts.findById(session.getCartId()).ifPresent(cart -> {
            cart.setStatus(CartStatus.CHECKED_OUT);
            carts.save(cart);
        });

        fulfillmentOrchestrator.createFromOrder(order, session.getCustomerId());
        return orders.save(order);
    }

    public java.util.Optional<CommerceOrder> findByCheckoutSession(UUID checkoutSessionId) {
        return orders.findByCheckoutSessionId(checkoutSessionId);
    }

    private String generateOrderNumber(UUID organizationId) {
        long seq = orders.countByOrganizationId(organizationId) + 1;
        return "CO-" + String.format("%06d", seq);
    }
}
