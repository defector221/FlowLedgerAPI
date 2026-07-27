package com.flowledger.inventory.allocation;

import java.math.BigDecimal;
import java.util.UUID;

/** Input for allocation engine operations. */
public record AllocationRequest(
        UUID organizationId,
        UUID productId,
        UUID warehouseId,
        BigDecimal quantity,
        UUID preferredBatchId) {}
