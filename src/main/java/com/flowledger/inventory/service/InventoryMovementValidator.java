package com.flowledger.inventory.service;

import com.flowledger.common.exception.BusinessException;
import com.flowledger.inventory.repository.InventoryTransactionRepository;
import com.flowledger.inventory.repository.StockReservationRepository;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Central negative-stock guard using {@link Organization#isAllowNegativeStock()}. */
@Component
public class InventoryMovementValidator {
    private final OrganizationRepository organizations;
    private final InventoryTransactionRepository transactions;
    private final StockReservationRepository reservations;

    public InventoryMovementValidator(
            OrganizationRepository organizations,
            InventoryTransactionRepository transactions,
            StockReservationRepository reservations) {
        this.organizations = organizations;
        this.transactions = transactions;
        this.reservations = reservations;
    }

    public void validateOutbound(UUID orgId, UUID productId, UUID warehouseId, BigDecimal qty) {
        if (qty == null || qty.signum() <= 0) {
            return;
        }
        Organization org = organizations.findById(orgId).orElseThrow();
        if (org.isAllowNegativeStock()) {
            return;
        }
        BigDecimal onHand = n(transactions.stockBalance(orgId, productId, warehouseId));
        BigDecimal reserved = n(reservations.activeReservedQty(orgId, productId, warehouseId));
        BigDecimal available = onHand.subtract(reserved);
        if (available.compareTo(qty) < 0) {
            throw new BusinessException("Insufficient stock for product " + productId + " in warehouse " + warehouseId
                    + " (available=" + available + ", requested=" + qty + ")");
        }
    }

    private static BigDecimal n(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
