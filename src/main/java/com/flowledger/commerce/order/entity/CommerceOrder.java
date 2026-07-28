package com.flowledger.commerce.order.entity;

import com.flowledger.commerce.common.CommerceGlobalEntity;
import com.flowledger.commerce.fulfillment.FulfillmentType;
import com.flowledger.commerce.order.domain.CommerceOrderStatus;
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
@Table(name = "commerce_orders")
@Getter
@Setter
@NoArgsConstructor
public class CommerceOrder extends CommerceGlobalEntity {
    @Column(name = "order_number", nullable = false)
    private String orderNumber;

    @Column(name = "checkout_session_id", unique = true)
    private UUID checkoutSessionId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "store_id", nullable = false, updatable = false)
    private UUID storeId;

    @Column(name = "erp_customer_id")
    private UUID erpCustomerId;

    @Column(name = "erp_sales_order_id")
    private UUID erpSalesOrderId;

    @Column(name = "erp_invoice_id")
    private UUID erpInvoiceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommerceOrderStatus status = CommerceOrderStatus.PLACED;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_type", nullable = false)
    private FulfillmentType fulfillmentType;

    @Column(name = "address_id")
    private UUID addressId;

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

    @Column(name = "placed_at", nullable = false)
    private OffsetDateTime placedAt = OffsetDateTime.now();

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Column(name = "cancelled_at")
    private OffsetDateTime cancelledAt;
}
