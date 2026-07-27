package com.flowledger.inventory.allocation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Selected inventory for an allocated line. */
public record AllocatedInventory(
        UUID batchId,
        UUID warehouseId,
        String warehouseName,
        String batchNumber,
        BigDecimal quantity,
        LocalDate expiryDate,
        LocalDate receivedDate,
        String lotNumber,
        AllocationMode allocationMode) {}
