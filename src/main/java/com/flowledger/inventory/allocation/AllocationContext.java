package com.flowledger.inventory.allocation;

import java.util.UUID;

/** Context passed to allocation strategies. */
public record AllocationContext(UUID organizationId, UUID warehouseId, UUID preferredWarehouseId) {}
