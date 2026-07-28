package com.flowledger.commerce.fulfillment.engine;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.flowledger.commerce.fulfillment.order.domain.FulfillmentOrderStatus;
import com.flowledger.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class StatusTransitionRulesTest {
    private final StatusTransitionRules rules = new StatusTransitionRules();

    @Test
    void allowsHappyPathTransitions() {
        assertDoesNotThrow(() -> rules.assertTransition(FulfillmentOrderStatus.CREATED, FulfillmentOrderStatus.ACCEPTED));
        assertDoesNotThrow(() -> rules.assertTransition(FulfillmentOrderStatus.ACCEPTED, FulfillmentOrderStatus.PICKING));
        assertDoesNotThrow(() -> rules.assertTransition(FulfillmentOrderStatus.PICKING, FulfillmentOrderStatus.PACKING));
        assertDoesNotThrow(() -> rules.assertTransition(FulfillmentOrderStatus.PACKING, FulfillmentOrderStatus.READY));
        assertDoesNotThrow(() -> rules.assertTransition(FulfillmentOrderStatus.READY, FulfillmentOrderStatus.FULFILLING));
        assertDoesNotThrow(() -> rules.assertTransition(FulfillmentOrderStatus.FULFILLING, FulfillmentOrderStatus.COMPLETED));
    }

    @Test
    void rejectsSkipTransitions() {
        assertThrows(BusinessException.class, () -> rules.assertTransition(FulfillmentOrderStatus.CREATED, FulfillmentOrderStatus.READY));
    }
}
