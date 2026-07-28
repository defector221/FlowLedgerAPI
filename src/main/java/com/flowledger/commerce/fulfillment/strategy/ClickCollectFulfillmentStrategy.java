package com.flowledger.commerce.fulfillment.strategy;

import com.flowledger.commerce.fulfillment.FulfillmentType;
import com.flowledger.commerce.fulfillment.engine.FulfillmentContext;
import com.flowledger.commerce.fulfillment.order.domain.FulfillmentSubStatus;
import com.flowledger.commerce.fulfillment.verification.QrTokenService;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ClickCollectFulfillmentStrategy implements FulfillmentStrategy {
    private final PickupFulfillmentStrategy pickupStrategy;
    private final QrTokenService qrTokens;

    public ClickCollectFulfillmentStrategy(PickupFulfillmentStrategy pickupStrategy, QrTokenService qrTokens) {
        this.pickupStrategy = pickupStrategy;
        this.qrTokens = qrTokens;
    }

    @Override
    public FulfillmentType type() {
        return FulfillmentType.CLICK_AND_COLLECT;
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
    public void onAccepted(FulfillmentContext ctx) {
        pickupStrategy.onAccepted(ctx);
    }

    @Override
    public void onReady(FulfillmentContext ctx) {
        pickupStrategy.onReady(ctx);
        qrTokens.issueCollectToken(ctx.fulfillmentOrder().getId());
        ctx.fulfillmentOrder().setSubStatus(FulfillmentSubStatus.QR_GENERATED);
    }

    @Override
    public void onFulfillmentStart(FulfillmentContext ctx) {}

    @Override
    public void onComplete(FulfillmentContext ctx) {}

    @Override
    public void onCancel(FulfillmentContext ctx) {}

    @Override
    public List<String> milestoneLabels() {
        return List.of("Accepted", "Picking", "Packing", "Ready", "QR Generated", "QR Verified", "Completed");
    }
}
