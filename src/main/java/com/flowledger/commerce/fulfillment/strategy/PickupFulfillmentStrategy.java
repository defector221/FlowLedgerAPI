package com.flowledger.commerce.fulfillment.strategy;

import com.flowledger.commerce.fulfillment.FulfillmentType;
import com.flowledger.commerce.fulfillment.engine.FulfillmentContext;
import com.flowledger.commerce.fulfillment.pickup.entity.PickupSession;
import com.flowledger.commerce.fulfillment.pickup.domain.PickupSessionStatus;
import com.flowledger.commerce.fulfillment.pickup.repository.PickupSessionRepository;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PickupFulfillmentStrategy implements FulfillmentStrategy {
    private final PickupSessionRepository pickupSessions;

    public PickupFulfillmentStrategy(PickupSessionRepository pickupSessions) {
        this.pickupSessions = pickupSessions;
    }

    @Override
    public FulfillmentType type() {
        return FulfillmentType.STORE_PICKUP;
    }

    @Override
    public boolean requiresPicking() {
        return true;
    }

    @Override
    public boolean requiresPacking() {
        return true;
    }

    @Override
    public void onAccepted(FulfillmentContext ctx) {}

    @Override
    public void onReady(FulfillmentContext ctx) {
        PickupSession session = new PickupSession();
        session.setFulfillmentOrderId(ctx.fulfillmentOrder().getId());
        session.setCustomerId(ctx.fulfillmentOrder().getCustomerId());
        session.setStoreId(ctx.fulfillmentOrder().getStoreId());
        session.setStatus(PickupSessionStatus.WAITING);
        pickupSessions.save(session);
    }

    @Override
    public void onFulfillmentStart(FulfillmentContext ctx) {}

    @Override
    public void onComplete(FulfillmentContext ctx) {}

    @Override
    public void onCancel(FulfillmentContext ctx) {}

    @Override
    public List<String> milestoneLabels() {
        return List.of("Accepted", "Picking", "Packing", "Ready", "Customer Arrived", "Picked Up");
    }
}
