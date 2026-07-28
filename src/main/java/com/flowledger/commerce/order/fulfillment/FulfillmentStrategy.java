package com.flowledger.commerce.order.fulfillment;

import com.flowledger.commerce.checkout.entity.CommerceCheckoutSession;
import com.flowledger.commerce.order.entity.CommerceOrder;
import java.util.UUID;

public interface FulfillmentStrategy {
    FulfillmentResult fulfill(CommerceCheckoutSession session, CommerceOrder order, UUID erpCustomerId);

    record FulfillmentResult(UUID erpSalesOrderId, UUID erpInvoiceId) {}
}
