package com.flowledger.commerce.inventory;

import com.flowledger.commerce.common.CommerceTenantScope;
import com.flowledger.commerce.store.repository.StoreCommerceProfileRepository;
import com.flowledger.inventory.allocation.ReservationAvailabilityService;
import com.flowledger.retail.entity.RetailStore;
import com.flowledger.retail.repository.RetailStoreRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sellable qty = warehouse on-hand minus all active soft locks (cart + order reservations). */
@Service
@Transactional(readOnly = true)
public class CommerceSellableInventoryService {
    private final StoreCommerceProfileRepository storeProfiles;
    private final RetailStoreRepository stores;
    private final ReservationAvailabilityService availability;

    public CommerceSellableInventoryService(
            StoreCommerceProfileRepository storeProfiles,
            RetailStoreRepository stores,
            ReservationAvailabilityService availability) {
        this.storeProfiles = storeProfiles;
        this.stores = stores;
        this.availability = availability;
    }

    public BigDecimal sellableQty(UUID organizationId, UUID storeId, UUID productId) {
        if (!publishesInventory(storeId)) {
            return null;
        }
        return CommerceTenantScope.run(organizationId, () -> {
            RetailStore store = stores
                    .findByIdAndOrganizationIdAndDeletedFalse(storeId, organizationId)
                    .orElse(null);
            if (store == null || store.getWarehouseId() == null) {
                return BigDecimal.ZERO;
            }
            return availability.warehouseAvailable(organizationId, productId, store.getWarehouseId(), null);
        });
    }

    public boolean publishesInventory(UUID storeId) {
        return storeProfiles.findByStoreId(storeId).map(p -> p.isPublishInventory()).orElse(false);
    }
}
