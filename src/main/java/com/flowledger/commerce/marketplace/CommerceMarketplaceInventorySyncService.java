package com.flowledger.commerce.marketplace;

import com.flowledger.commerce.common.CommerceTenantScope;
import com.flowledger.commerce.inventory.CommerceSellableInventoryService;
import com.flowledger.commerce.order.entity.CommerceOrder;
import com.flowledger.commerce.order.entity.CommerceOrderLine;
import com.flowledger.commerce.order.repository.CommerceOrderLineRepository;
import com.flowledger.commerce.order.repository.CommerceOrderRepository;
import com.flowledger.commerce.store.entity.StoreCommerceProfile;
import com.flowledger.commerce.store.repository.StoreCommerceProfileRepository;
import com.flowledger.inventory.service.InventoryService;
import com.flowledger.retail.entity.RetailStore;
import com.flowledger.retail.repository.RetailStoreRepository;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CommerceMarketplaceInventorySyncService {
    private static final Logger log = LoggerFactory.getLogger(CommerceMarketplaceInventorySyncService.class);

    private final CommerceOrderRepository orders;
    private final CommerceOrderLineRepository orderLines;
    private final StoreCommerceProfileRepository storeProfiles;
    private final RetailStoreRepository stores;
    private final InventoryService inventory;
    private final MarketplaceSyncService marketplaceSync;
    private final CommerceSellableInventoryService sellableInventory;

    public CommerceMarketplaceInventorySyncService(
            CommerceOrderRepository orders,
            CommerceOrderLineRepository orderLines,
            StoreCommerceProfileRepository storeProfiles,
            RetailStoreRepository stores,
            InventoryService inventory,
            MarketplaceSyncService marketplaceSync,
            CommerceSellableInventoryService sellableInventory) {
        this.orders = orders;
        this.orderLines = orderLines;
        this.storeProfiles = storeProfiles;
        this.stores = stores;
        this.inventory = inventory;
        this.marketplaceSync = marketplaceSync;
        this.sellableInventory = sellableInventory;
    }

    public void syncOrder(UUID orderId, UUID actorId) {
        orders.findById(orderId).ifPresent(order -> syncOrder(order, actorId));
    }

    public void syncOrder(CommerceOrder order, UUID actorId) {
        StoreCommerceProfile profile = storeProfiles.findByStoreId(order.getStoreId()).orElse(null);
        if (profile == null || !profile.isPublishInventory()) {
            return;
        }

        UUID organizationId = order.getOrganizationId();
        CommerceTenantScope.runVoid(organizationId, () -> {
            RetailStore store = stores
                    .findByIdAndOrganizationIdAndDeletedFalse(order.getStoreId(), organizationId)
                    .orElse(null);
            if (store == null || store.getWarehouseId() == null) {
                log.debug("Skipping marketplace inventory sync: store warehouse missing for order {}", order.getId());
                return;
            }

            List<CommerceOrderLine> lines = orderLines.findByOrderIdOrderByCreatedAtAsc(order.getId());
            Set<UUID> productIds = new LinkedHashSet<>();
            for (CommerceOrderLine line : lines) {
                if (line.getProductId() != null) {
                    productIds.add(line.getProductId());
                }
            }
            syncProducts(organizationId, order.getStoreId(), store.getWarehouseId(), productIds, actorId);
        });
    }

    private void syncProducts(
            UUID organizationId, UUID storeId, UUID warehouseId, Set<UUID> productIds, UUID actorId) {
        for (UUID productId : productIds) {
            BigDecimal available = sellableInventory.sellableQty(organizationId, storeId, productId);
            if (available == null) {
                available = inventory.getStock(productId, warehouseId).available();
            }
            if (available == null) {
                available = BigDecimal.ZERO;
            }
            marketplaceSync.syncInventory(storeId, productId, available, actorId);
            log.debug(
                    "Synced marketplace inventory for store {} product {} qty {}",
                    storeId,
                    productId,
                    available);
        }
    }
}
