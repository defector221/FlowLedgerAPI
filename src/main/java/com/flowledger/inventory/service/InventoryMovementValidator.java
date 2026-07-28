package com.flowledger.inventory.service;

import com.flowledger.common.exception.BusinessException;
import com.flowledger.inventory.allocation.ReservationAvailabilityService;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Central negative-stock guard using {@link Organization#isAllowNegativeStock()}. */
@Component
public class InventoryMovementValidator {
    private final OrganizationRepository organizations;
    private final ReservationAvailabilityService availability;

    public InventoryMovementValidator(
            OrganizationRepository organizations, ReservationAvailabilityService availability) {
        this.organizations = organizations;
        this.availability = availability;
    }

    public void validateOutbound(UUID orgId, UUID productId, UUID warehouseId, BigDecimal qty) {
        if (qty == null || qty.signum() <= 0) {
            return;
        }
        Organization org = organizations.findById(orgId).orElseThrow();
        if (org.isAllowNegativeStock()) {
            return;
        }
        BigDecimal available = availability.warehouseAvailable(orgId, productId, warehouseId, null);
        if (available.compareTo(qty) < 0) {
            throw new BusinessException("Insufficient stock for product " + productId + " in warehouse " + warehouseId
                    + " (available=" + available + ", requested=" + qty + ")");
        }
    }
}
