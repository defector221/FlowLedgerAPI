package com.flowledger.inventory.allocation;

import com.flowledger.inventory.repository.InventoryBatchRepository;
import com.flowledger.inventory.repository.InventoryTransactionRepository;
import com.flowledger.inventory.repository.StockReservationRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Computes on-hand minus active reservations for allocation. */
@Component
public class ReservationAvailabilityService {
    private final InventoryTransactionRepository transactions;
    private final StockReservationRepository reservations;
    private final InventoryBatchRepository batches;

    public ReservationAvailabilityService(
            InventoryTransactionRepository transactions,
            StockReservationRepository reservations,
            InventoryBatchRepository batches) {
        this.transactions = transactions;
        this.reservations = reservations;
        this.batches = batches;
    }

    public BigDecimal warehouseAvailable(UUID orgId, UUID productId, UUID warehouseId, UUID excludeReservationId) {
        BigDecimal onHand = n(transactions.stockBalance(orgId, productId, warehouseId));
        BigDecimal reserved = reservations.activeReservedQty(orgId, productId, warehouseId, excludeReservationId);
        return onHand.subtract(reserved).max(BigDecimal.ZERO);
    }

    public BigDecimal batchAvailable(UUID orgId, UUID batchId, UUID excludeReservationId) {
        return batches.findByIdAndOrganizationId(batchId, orgId)
                .map(batch -> {
                    BigDecimal reserved = reservations.activeReservedQtyByBatch(orgId, batchId, excludeReservationId);
                    return n(batch.getQuantity()).subtract(reserved).max(BigDecimal.ZERO);
                })
                .orElse(BigDecimal.ZERO);
    }

    private static BigDecimal n(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
