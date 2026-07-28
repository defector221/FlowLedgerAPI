package com.flowledger.commerce.events;

import java.util.UUID;

public class CommerceReservationExpiredEvent extends CommerceDomainEvent {
    private final UUID cartId;

    public CommerceReservationExpiredEvent(
            Object source, UUID organizationId, UUID actorId, UUID reservationId, UUID cartId) {
        super(source, organizationId, actorId, "CommerceInventoryReservation", reservationId);
        this.cartId = cartId;
    }

    public UUID cartId() {
        return cartId;
    }
}
