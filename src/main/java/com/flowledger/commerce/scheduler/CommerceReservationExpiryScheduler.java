package com.flowledger.commerce.scheduler;

import com.flowledger.commerce.checkout.domain.CheckoutSessionStatus;
import com.flowledger.commerce.checkout.entity.CommerceCheckoutSession;
import com.flowledger.commerce.checkout.repository.CommerceCheckoutSessionRepository;
import com.flowledger.commerce.reservation.CommerceInventoryReservationService;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CommerceReservationExpiryScheduler {
    private static final Logger log = LoggerFactory.getLogger(CommerceReservationExpiryScheduler.class);

    private final CommerceInventoryReservationService reservations;
    private final CommerceCheckoutSessionRepository checkoutSessions;

    public CommerceReservationExpiryScheduler(
            CommerceInventoryReservationService reservations,
            CommerceCheckoutSessionRepository checkoutSessions) {
        this.reservations = reservations;
        this.checkoutSessions = checkoutSessions;
    }

    @Scheduled(fixedDelayString = "${flowledger.commerce.cart.reservation-poll-ms:60000}")
    @Transactional
    public void expireReservations() {
        int expired = reservations.expireStale();
        if (expired > 0) {
            log.info("Expired {} commerce inventory reservations", expired);
        }
    }

    @Scheduled(fixedDelayString = "${flowledger.commerce.cart.checkout-expiry-poll-ms:120000}")
    @Transactional
    public void expireCheckoutSessions() {
        for (CommerceCheckoutSession session :
                checkoutSessions.findByStatusAndExpiresAtBefore(CheckoutSessionStatus.PAYMENT_PENDING, OffsetDateTime.now())) {
            session.setStatus(CheckoutSessionStatus.EXPIRED);
            checkoutSessions.save(session);
        }
    }
}
