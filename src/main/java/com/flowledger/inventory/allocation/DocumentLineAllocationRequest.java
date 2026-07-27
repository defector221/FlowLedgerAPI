package com.flowledger.inventory.allocation;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Input for reserving inventory against a document line. */
public record DocumentLineAllocationRequest(
        UUID organizationId,
        UUID productId,
        UUID warehouseId,
        BigDecimal quantity,
        UUID preferredBatchId,
        AllocationMode preferredMode,
        String referenceType,
        UUID referenceId,
        UUID lineReferenceId,
        UUID excludeReservationId,
        OffsetDateTime expiresAt) {}
