package com.flowledger.commerce.order.erp;

import com.flowledger.commerce.checkout.entity.CommerceCheckoutSession;
import com.flowledger.commerce.fulfillment.FulfillmentType;
import com.flowledger.commerce.order.entity.CommerceOrder;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ErpDocumentService {
    private final PickupErpDocumentStrategy pickupErp;
    private final DeliveryErpDocumentStrategy deliveryErp;

    public ErpDocumentService(PickupErpDocumentStrategy pickupErp, DeliveryErpDocumentStrategy deliveryErp) {
        this.pickupErp = pickupErp;
        this.deliveryErp = deliveryErp;
    }

    public ErpDocumentStrategy.ErpDocumentResult postAtMilestone(
            FulfillmentType type,
            CommerceCheckoutSession session,
            CommerceOrder order,
            UUID erpCustomerId,
            ErpDocumentStrategy.ErpMilestone milestone) {
        return resolve(type).fulfillAtMilestone(session, order, erpCustomerId, milestone);
    }

    private ErpDocumentStrategy resolve(FulfillmentType type) {
        return switch (type) {
            case HOME_DELIVERY -> deliveryErp;
            case STORE_PICKUP, CLICK_AND_COLLECT, SCAN_AND_GO -> pickupErp;
        };
    }
}
