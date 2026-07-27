package com.flowledger.inventory.allocation;

import java.util.List;

public interface InventoryAllocationEngine {
    AllocationResult allocate(AllocationRequest request);

    AllocationResult allocateWithBatch(AllocationRequest request, java.util.UUID batchId);

    CheckoutValidationResult revalidateForCheckout(List<CartLineAllocation> cartLines);

    ReservationResult reserveForDocument(DocumentLineAllocationRequest request);

    void releaseByReference(String referenceType, java.util.UUID referenceId);

    void consumeByReference(String referenceType, java.util.UUID referenceId);
}
