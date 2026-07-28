package com.flowledger.commerce.fulfillment.strategy;

import com.flowledger.commerce.fulfillment.FulfillmentType;
import com.flowledger.commerce.fulfillment.engine.FulfillmentContext;
import java.util.List;

public interface FulfillmentStrategy {
    FulfillmentType type();

    boolean requiresPicking();

    boolean requiresPacking();

    void onAccepted(FulfillmentContext ctx);

    void onReady(FulfillmentContext ctx);

    void onFulfillmentStart(FulfillmentContext ctx);

    void onComplete(FulfillmentContext ctx);

    void onCancel(FulfillmentContext ctx);

    List<String> milestoneLabels();
}
