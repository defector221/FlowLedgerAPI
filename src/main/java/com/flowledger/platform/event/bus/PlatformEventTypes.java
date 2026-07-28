package com.flowledger.platform.event.bus;

public final class PlatformEventTypes {
    private PlatformEventTypes() {}

    public static final String ORDER_CREATED = "OrderCreated";
    public static final String ORDER_COMPLETED = "OrderCompleted";
    public static final String PAYMENT_SUCCEEDED = "PaymentSucceeded";
    public static final String CUSTOMER_REGISTERED = "CustomerRegistered";
    public static final String STORE_PUBLISHED = "StorePublished";
    public static final String PRODUCT_UPDATED = "ProductUpdated";
    public static final String PROMOTION_APPLIED = "PromotionApplied";
    public static final String REWARD_CREDITED = "RewardCredited";
    public static final String CART_CREATED = "CartCreated";
}
