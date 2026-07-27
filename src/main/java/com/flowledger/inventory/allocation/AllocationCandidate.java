package com.flowledger.inventory.allocation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One eligible inventory source for allocation or user selection. */
public record AllocationCandidate(
        UUID batchId,
        UUID warehouseId,
        String warehouseName,
        String batchNumber,
        BigDecimal availableQty,
        LocalDate expiryDate,
        LocalDate receivedDate,
        String lotNumber,
        String qualityStatus,
        BigDecimal reservedQty) {}
