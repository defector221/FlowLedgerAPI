package com.flowledger.commerce.fulfillment.engine;

import com.flowledger.commerce.fulfillment.order.domain.FulfillmentOrderStatus;
import com.flowledger.commerce.fulfillment.order.domain.FulfillmentSubStatus;
import com.flowledger.common.exception.BusinessException;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class StatusTransitionRules {
    private static final Map<FulfillmentOrderStatus, Set<FulfillmentOrderStatus>> ALLOWED = Map.of(
            FulfillmentOrderStatus.CREATED, EnumSet.of(FulfillmentOrderStatus.ACCEPTED, FulfillmentOrderStatus.CANCELLED),
            FulfillmentOrderStatus.ACCEPTED, EnumSet.of(FulfillmentOrderStatus.PICKING, FulfillmentOrderStatus.CANCELLED),
            FulfillmentOrderStatus.PICKING, EnumSet.of(FulfillmentOrderStatus.PACKING, FulfillmentOrderStatus.CANCELLED),
            FulfillmentOrderStatus.PACKING, EnumSet.of(FulfillmentOrderStatus.READY, FulfillmentOrderStatus.CANCELLED),
            FulfillmentOrderStatus.READY, EnumSet.of(FulfillmentOrderStatus.FULFILLING, FulfillmentOrderStatus.CANCELLED),
            FulfillmentOrderStatus.FULFILLING,
                    EnumSet.of(FulfillmentOrderStatus.COMPLETED, FulfillmentOrderStatus.CANCELLED));

    public void assertTransition(FulfillmentOrderStatus from, FulfillmentOrderStatus to) {
        if (from == to) return;
        Set<FulfillmentOrderStatus> allowed = ALLOWED.get(from);
        if (allowed == null || !allowed.contains(to)) {
            throw new BusinessException("Invalid fulfillment transition: " + from + " -> " + to);
        }
    }

    public boolean canSkipToReady(FulfillmentSubStatus subStatus) {
        return subStatus != null;
    }
}
