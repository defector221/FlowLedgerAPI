package com.flowledger.inventory.allocation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.flowledger.inventory.repository.InventoryTransactionRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WarehousePoolAllocatorTest {
    @Mock
    InventoryTransactionRepository transactions;

    @InjectMocks
    WarehousePoolAllocator allocator;

    @Test
    void autoAllocatesWhenStockSufficient() {
        UUID org = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        UUID warehouse = UUID.randomUUID();
        when(transactions.stockBalance(org, product, warehouse)).thenReturn(new BigDecimal("10"));

        AllocationResult result = allocator.allocate(
                new AllocationRequest(org, product, warehouse, new BigDecimal("3"), null));

        assertEquals(AllocationStatus.AUTO_ALLOCATED, result.status());
        assertEquals(AllocationMode.WAREHOUSE_POOL, result.allocated().allocationMode());
    }

    @Test
    void outOfStockWhenInsufficient() {
        UUID org = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        UUID warehouse = UUID.randomUUID();
        when(transactions.stockBalance(org, product, warehouse)).thenReturn(new BigDecimal("1"));

        AllocationResult result = allocator.allocate(
                new AllocationRequest(org, product, warehouse, new BigDecimal("3"), null));

        assertEquals(AllocationStatus.OUT_OF_STOCK, result.status());
    }
}
