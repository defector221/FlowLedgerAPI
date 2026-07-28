package com.flowledger.commerce.cart.entity;

import com.flowledger.commerce.cart.domain.CartStatus;
import com.flowledger.commerce.channel.SalesChannel;
import com.flowledger.commerce.common.CommerceGlobalEntity;
import com.flowledger.commerce.fulfillment.FulfillmentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "commerce_carts")
@Getter
@Setter
@NoArgsConstructor
public class CommerceCart extends CommerceGlobalEntity {
    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "organization_id", nullable = false, updatable = false)
    private UUID organizationId;

    @Column(name = "store_id", nullable = false, updatable = false)
    private UUID storeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CartStatus status = CartStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SalesChannel channel = SalesChannel.WEB;

    @Enumerated(EnumType.STRING)
    @Column(name = "fulfillment_type")
    private FulfillmentType fulfillmentType;

    @Column(nullable = false)
    private String currency = "INR";

    @Column(nullable = false)
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "discount_total", nullable = false)
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(name = "tax_total", nullable = false)
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(name = "grand_total", nullable = false)
    private BigDecimal grandTotal = BigDecimal.ZERO;

    @Column(name = "item_count", nullable = false)
    private int itemCount;
}
