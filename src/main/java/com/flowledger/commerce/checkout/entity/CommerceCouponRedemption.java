package com.flowledger.commerce.checkout.entity;

import com.flowledger.commerce.common.CommerceGlobalEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "commerce_coupon_redemptions")
@Getter
@Setter
@NoArgsConstructor
public class CommerceCouponRedemption extends CommerceGlobalEntity {
    @Column(name = "checkout_session_id", nullable = false, updatable = false)
    private UUID checkoutSessionId;

    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "coupon_code", nullable = false)
    private String couponCode;

    @Column(name = "discount_applied", nullable = false)
    private BigDecimal discountApplied = BigDecimal.ZERO;

    @Column(name = "redeemed_at", nullable = false)
    private OffsetDateTime redeemedAt = OffsetDateTime.now();
}
