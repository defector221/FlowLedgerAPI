package com.flowledger.commerce.referral;

import com.flowledger.platform.event.bus.PlatformEventEnvelope;
import com.flowledger.platform.event.bus.PlatformEventDispatcher;
import com.flowledger.platform.event.bus.PlatformEventTypes;
import jakarta.annotation.PostConstruct;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReferralRewardConsumer {
    private final PlatformEventDispatcher dispatcher;
    private final ReferralService referrals;

    public ReferralRewardConsumer(PlatformEventDispatcher dispatcher, ReferralService referrals) {
        this.dispatcher = dispatcher;
        this.referrals = referrals;
    }

    @PostConstruct
    void register() {
        dispatcher.register(PlatformEventTypes.ORDER_COMPLETED, this::onOrderCompleted);
    }

    @Transactional
    void onOrderCompleted(PlatformEventEnvelope event) {
        UUID orderId = uuid(event.payload().get("orderId"));
        UUID customerId = uuid(event.payload().get("customerId"));
        if (orderId == null || customerId == null || event.organizationId() == null) return;
        referrals.rewardOnFirstOrder(event.organizationId(), customerId, orderId);
    }

    private static UUID uuid(Object v) {
        if (v == null || v.toString().isBlank()) return null;
        return UUID.fromString(v.toString());
    }
}
