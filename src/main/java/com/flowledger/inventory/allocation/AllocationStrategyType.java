package com.flowledger.inventory.allocation;

/** Organization-configured batch allocation strategy. */
public enum AllocationStrategyType {
    DEFAULT,
    FIFO,
    FEFO,
    LIFO,
    HIGHEST_QUANTITY,
    PREFERRED_WAREHOUSE;

    public static AllocationStrategyType fromSetting(String value) {
        if (value == null || value.isBlank()) {
            return FIFO;
        }
        try {
            return AllocationStrategyType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return FIFO;
        }
    }

    public AllocationStrategyType effective() {
        return this == DEFAULT ? FIFO : this;
    }
}
