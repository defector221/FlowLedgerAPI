package com.flowledger.inventory.allocation;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Non-batch products: warehouse pool only — never CONFLICT. */
@Component
public class WarehousePoolAllocator {
    private final ReservationAvailabilityService availability;

    public WarehousePoolAllocator(ReservationAvailabilityService availability) {
        this.availability = availability;
    }

    public AllocationResult allocate(AllocationRequest request) {
        BigDecimal available = availability.warehouseAvailable(
                request.organizationId(),
                request.productId(),
                request.warehouseId(),
                request.excludeReservationId());
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
}
