package com.flowledger.commerce.payment.entity;

import com.flowledger.commerce.common.CommerceGlobalEntity;
import com.flowledger.commerce.payment.domain.CommercePaymentProvider;
import com.flowledger.commerce.payment.domain.PaymentSessionStatus;
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
@Table(name = "commerce_payment_sessions")
@Getter
@Setter
@NoArgsConstructor
public class CommercePaymentSession extends CommerceGlobalEntity {
    @Column(name = "checkout_session_id", nullable = false, updatable = false)
    private UUID checkoutSessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CommercePaymentProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentSessionStatus status = PaymentSessionStatus.PENDING;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency = "INR";

    @Column(name = "gateway_order_id")
    private String gatewayOrderId;

    @Column(name = "gateway_payment_id")
    private String gatewayPaymentId;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "raw_response", columnDefinition = "jsonb")
    private String rawResponse;

    @Column(name = "paid_at")
    private OffsetDateTime paidAt;
}
