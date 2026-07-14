package com.flowledger.inventory.dto;

import com.flowledger.inventory.entity.InventoryTransaction.Type;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class InventoryDtos {
    private InventoryDtos() {}

    public record PostTransaction(
            @NotNull Type type,
            @NotNull UUID productId,
            @NotNull UUID warehouseId,
            BigDecimal inward,
            BigDecimal outward,
            String referenceType,
            UUID referenceId,
            String referenceNumber,
            String idempotencyKey,
            String batchNumber,
            String serialNumber,
            LocalDate expiryDate,
            BigDecimal unitCost,
            String notes,
            LocalDate transactionDate) {}

    public record Adjustment(
            @NotNull UUID productId, @NotNull UUID warehouseId, @NotNull BigDecimal quantity, String notes) {}

    public record Transfer(
            @NotNull UUID productId,
            @NotNull UUID fromWarehouseId,
            @NotNull UUID toWarehouseId,
            @Positive BigDecimal quantity,
            String notes) {}

    public record Stock(UUID productId, UUID warehouseId, BigDecimal available) {}

    public record StockPosition(
            UUID productId,
            String productName,
            String sku,
            BigDecimal available,
            BigDecimal draftReserved,
            BigDecimal minimumStockLevel,
            BigDecimal reorderLevel) {}

    public record Ledger(
            LocalDate date,
            Type type,
            String reference,
            BigDecimal inward,
            BigDecimal outward,
            BigDecimal runningBalance) {}

    public record Alert(UUID productId, String productName, BigDecimal available, BigDecimal threshold) {}
}
