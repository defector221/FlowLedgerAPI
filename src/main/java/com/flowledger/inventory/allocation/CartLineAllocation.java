package com.flowledger.inventory.allocation;

import java.math.BigDecimal;
import java.util.UUID;

/** Cart line snapshot used during checkout re-validation. */
public record CartLineAllocation(
        UUID organizationId,
        UUID lineId,
        UUID productId,
        UUID warehouseId,
        BigDecimal quantity,
        UUID inventoryBatchId,
        AllocationMode allocationMode) {}
