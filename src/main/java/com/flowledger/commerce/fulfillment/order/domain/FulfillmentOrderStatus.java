package com.flowledger.commerce.fulfillment.order.domain;

public enum FulfillmentOrderStatus {
    CREATED,
    ACCEPTED,
    PICKING,
    PACKING,
    READY,
    FULFILLING,
    COMPLETED,
    CANCELLED
}
