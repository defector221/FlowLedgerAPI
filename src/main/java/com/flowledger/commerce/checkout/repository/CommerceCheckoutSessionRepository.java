package com.flowledger.commerce.checkout.repository;

import com.flowledger.commerce.checkout.domain.CheckoutSessionStatus;
import com.flowledger.commerce.checkout.entity.CommerceCheckoutSession;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommerceCheckoutSessionRepository extends JpaRepository<CommerceCheckoutSession, UUID> {
    Optional<CommerceCheckoutSession> findByIdAndCustomerId(UUID id, UUID customerId);

    List<CommerceCheckoutSession> findByStatusAndExpiresAtBefore(CheckoutSessionStatus status, OffsetDateTime expiresAt);
}
