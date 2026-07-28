package com.flowledger.commerce.common;

import com.flowledger.commerce.checkout.entity.CommerceCheckoutSession;
import com.flowledger.commerce.fulfillment.order.entity.FulfillmentOrder;
import com.flowledger.commerce.order.entity.CommerceOrder;
import java.util.UUID;

public final class CommerceOrganizations {
    private CommerceOrganizations() {}

    public static UUID resolve(CommerceOrder order, CommerceCheckoutSession session) {
        if (order != null && order.getOrganizationId() != null) {
            return order.getOrganizationId();
        }
        if (session != null && session.getOrganizationId() != null) {
            return session.getOrganizationId();
        }
        throw new IllegalStateException("Commerce organization id is not available");
    }

    public static UUID resolve(
            CommerceOrder order, CommerceCheckoutSession session, FulfillmentOrder fulfillmentOrder) {
        if (order != null && order.getOrganizationId() != null) {
            return order.getOrganizationId();
        }
        if (fulfillmentOrder != null && fulfillmentOrder.getOrganizationId() != null) {
            return fulfillmentOrder.getOrganizationId();
        }
        if (session != null && session.getOrganizationId() != null) {
            return session.getOrganizationId();
        }
        throw new IllegalStateException("Commerce organization id is not available");
    }
}
