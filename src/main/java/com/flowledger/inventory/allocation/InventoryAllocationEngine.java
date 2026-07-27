package com.flowledger.inventory.allocation;

import java.util.List;

public interface InventoryAllocationEngine {
    AllocationResult allocate(AllocationRequest request);

    AllocationResult allocateWithBatch(AllocationRequest request, java.util.UUID batchId);

    CheckoutValidationResult revalidateForCheckout(List<CartLineAllocation> cartLines);
}
