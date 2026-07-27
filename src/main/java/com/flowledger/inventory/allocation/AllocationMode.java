package com.flowledger.inventory.allocation;

/** How a POS line was allocated (persisted on pos_sale_lines). */
public enum AllocationMode {
    WAREHOUSE_POOL,
    BATCH_AUTO,
    BATCH_MANUAL
}
