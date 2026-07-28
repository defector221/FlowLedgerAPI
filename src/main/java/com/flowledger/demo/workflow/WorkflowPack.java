package com.flowledger.demo.workflow;

/** Feature flags for post-catalog seed stages. */
public record WorkflowPack(
        boolean loyaltyEnabled,
        boolean promotionsEnabled,
        boolean posHeavy,
        boolean creditSalesHeavy,
        boolean largePurchaseOrders,
        boolean batchExpiryHeavy,
        boolean serialTrackingHeavy,
        boolean fashionVariants,
        boolean onlineStore,
        boolean interStoreTransfers) {

    public static WorkflowPack retailDefault() {
        return new WorkflowPack(true, true, true, false, false, false, false, false, false, true);
    }

    public static WorkflowPack grocery() {
        return new WorkflowPack(true, true, true, false, false, true, false, false, false, true);
    }

    public static WorkflowPack fashion() {
        return new WorkflowPack(true, true, true, false, false, false, false, true, false, true);
    }

    public static WorkflowPack electronics() {
        return new WorkflowPack(true, true, true, false, false, false, true, false, false, true);
    }

    public static WorkflowPack pharmacy() {
        return new WorkflowPack(false, true, true, false, false, true, false, false, false, true);
    }

    public static WorkflowPack wholesale() {
        return new WorkflowPack(false, false, false, true, true, false, false, false, false, true);
    }

    public static WorkflowPack omni() {
        return new WorkflowPack(true, true, true, false, false, false, false, false, true, true);
    }
}
