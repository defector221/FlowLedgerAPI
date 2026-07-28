package com.flowledger.commerce.fulfillment.order.domain;

public enum FulfillmentSubStatus {
    NONE,
    QR_GENERATED,
    QR_VERIFIED,
    CUSTOMER_ARRIVED,
    PICKED_UP,
    DRIVER_ASSIGNED,
    OUT_FOR_DELIVERY,
    DELIVERED
}
