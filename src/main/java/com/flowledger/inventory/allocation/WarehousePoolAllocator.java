package com.flowledger.inventory.allocation;

import com.flowledger.inventory.repository.InventoryTransactionRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Non-batch products: warehouse pool only — never CONFLICT. */
@Component
public class WarehousePoolAllocator {
    private final InventoryTransactionRepository transactions;

    public WarehousePoolAllocator(InventoryTransactionRepository transactions) {
        this.transactions = transactions;
    }

    public AllocationResult allocate(AllocationRequest request) {
        BigDecimal available = n(transactions.stockBalance(
                request.organizationId(), request.productId(), request.warehouseId()));
        if (available.compareTo(request.quantity()) >= 0) {
            return AllocationResult.auto(new AllocatedInventory(
                    null,
                    request.warehouseId(),
                    null,
                    null,
                    request.quantity(),
                    null,
                    null,
                    null,
                    AllocationMode.WAREHOUSE_POOL));
        }
        return AllocationResult.outOfStock("Insufficient stock in warehouse");
    }

    private static BigDecimal n(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
