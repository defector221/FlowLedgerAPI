package com.flowledger.inventory.allocation;

/** Outcome of an allocation attempt. */
public enum AllocationStatus {
    AUTO_ALLOCATED,
    CONFLICT,
    OUT_OF_STOCK,
    INVALID_PRODUCT
}
