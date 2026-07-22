package com.flowledger.subscription.repository;

import com.flowledger.subscription.entity.PaymentWebhookEvent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, UUID> {
    Optional<PaymentWebhookEvent> findByEventId(String eventId);

    boolean existsByEventId(String eventId);
}
