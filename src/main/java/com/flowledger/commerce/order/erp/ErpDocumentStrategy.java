package com.flowledger.commerce.order.erp;

import com.flowledger.commerce.checkout.entity.CommerceCheckoutSession;
import com.flowledger.commerce.order.entity.CommerceOrder;
import java.util.UUID;

public interface ErpDocumentStrategy {
    ErpDocumentResult fulfillAtMilestone(
            CommerceCheckoutSession session, CommerceOrder order, UUID erpCustomerId, ErpMilestone milestone);

    record ErpDocumentResult(UUID erpSalesOrderId, UUID erpInvoiceId) {}

    enum ErpMilestone {
        ACCEPTED,
        OUT_FOR_DELIVERY,
        PICKED_UP,
        QR_VERIFIED,
        SCAN_EXIT_VERIFIED
    }
}
