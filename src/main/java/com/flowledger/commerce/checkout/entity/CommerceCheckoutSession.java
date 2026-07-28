package com.flowledger.commerce.checkout.entity;

import com.flowledger.commerce.checkout.domain.CheckoutSessionStatus;
import com.flowledger.commerce.common.CommerceGlobalEntity;
import com.flowledger.commerce.fulfillment.FulfillmentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "commerce_checkout_sessions")
@Getter
@Setter
@NoArgsConstructor
public class CommerceCheckoutSession extends CommerceGlobalEntity {
    @Column(name = "cart_id", nullable = false, updatable = false)
    private UUID cartId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "store_id", nullable = false, updatable = false)
    private UUID storeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CheckoutSessionStatus status = CheckoutSessionStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_type", nullable = false)
    private FulfillmentType fulfillmentType;

    @Column(name = "address_id")
    private UUID addressId;

    @Column(name = "coupon_code")
    private String couponCode;

    @Column(nullable = false)
    private String currency = "INR";

    @Column(nullable = false)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_total", nullable = false)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(name = "tax_total", nullable = false)
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(name = "shipping_total", nullable = false)
    private BigDecimal shippingTotal = BigDecimal.ZERO;

    @Column(name = "grand_total", nullable = false)
    private BigDecimal grandTotal = BigDecimal.ZERO;

    @Column(name = "pricing_snapshot", nullable = false, columnDefinition = "jsonb")
    private String pricingSnapshot = "{}";

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
}
