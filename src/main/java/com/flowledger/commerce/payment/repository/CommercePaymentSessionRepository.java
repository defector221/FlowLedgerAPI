package com.flowledger.commerce.payment.repository;

import com.flowledger.commerce.payment.entity.CommercePaymentSession;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommercePaymentSessionRepository extends JpaRepository<CommercePaymentSession, UUID> {
    Optional<CommercePaymentSession> findByIdempotencyKey(String idempotencyKey);

    Optional<CommercePaymentSession> findByCheckoutSessionIdAndStatus(
            UUID checkoutSessionId, com.flowledger.commerce.payment.domain.PaymentSessionStatus status);

    Optional<CommercePaymentSession> findFirstByCheckoutSessionIdOrderByCreatedAtDesc(UUID checkoutSessionId);

    Optional<CommercePaymentSession> findByGatewayOrderId(String gatewayOrderId);
}
