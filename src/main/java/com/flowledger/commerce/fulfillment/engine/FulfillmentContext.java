package com.flowledger.commerce.fulfillment.engine;

import com.flowledger.commerce.checkout.entity.CommerceCheckoutSession;
import com.flowledger.commerce.fulfillment.order.entity.FulfillmentOrder;
import com.flowledger.commerce.order.entity.CommerceOrder;
import com.flowledger.commerce.order.entity.CommerceOrderLine;
import java.util.List;
import java.util.UUID;

public record FulfillmentContext(
        FulfillmentOrder fulfillmentOrder,
        CommerceOrder commerceOrder,
        CommerceCheckoutSession checkoutSession,
        List<CommerceOrderLine> orderLines,
        UUID actorId) {}
