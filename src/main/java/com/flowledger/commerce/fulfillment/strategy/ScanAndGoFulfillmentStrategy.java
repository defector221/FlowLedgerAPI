package com.flowledger.commerce.fulfillment.strategy;

import com.flowledger.commerce.fulfillment.FulfillmentType;
import com.flowledger.commerce.fulfillment.engine.FulfillmentContext;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ScanAndGoFulfillmentStrategy implements FulfillmentStrategy {
    @Override
    public FulfillmentType type() {
        return FulfillmentType.SCAN_AND_GO;
    }

    @Override
    public boolean requiresPicking() {
        return false;
    }

    @Override
    public boolean requiresPacking() {
        return false;
    }

    @Override
    public void onAccepted(FulfillmentContext ctx) {}

    @Override
    public void onReady(FulfillmentContext ctx) {}

    @Override
    public void onFulfillmentStart(FulfillmentContext ctx) {}

    @Override
    public void onComplete(FulfillmentContext ctx) {}

    @Override
    public void onCancel(FulfillmentContext ctx) {}

    @Override
    public List<String> milestoneLabels() {
        return List.of("Session Open", "Paid", "Exit Verified", "Completed");
    }
}
