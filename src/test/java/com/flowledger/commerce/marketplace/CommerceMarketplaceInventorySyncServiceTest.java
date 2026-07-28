package com.flowledger.commerce.marketplace;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CommerceMarketplaceInventorySyncServiceTest {
    @Mock
    private CommerceOrderRepository orders;
    @Mock
    private CommerceOrderLineRepository orderLines;
    @Mock
    private StoreCommerceProfileRepository storeProfiles;
    @Mock
    private RetailStoreRepository stores;
    @Mock
    private InventoryService inventory;
    @Mock
    private MarketplaceSyncService marketplaceSync;
    @Mock
    private CommerceSellableInventoryService sellableInventory;

    private CommerceMarketplaceInventorySyncService service;

    @BeforeEach
    void setUp() {
        service = new CommerceMarketplaceInventorySyncService(
                orders, orderLines, storeProfiles, stores, inventory, marketplaceSync, sellableInventory);
    }

    @Test
    void syncOrderPublishesWarehouseQtyForEachLineProduct() {
        UUID orgId = UUID.randomUUID();
        UUID storeId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        CommerceOrder order = new CommerceOrder();
        order.setId(orderId);
        order.setOrganizationId(orgId);
        order.setStoreId(storeId);

        StoreCommerceProfile profile = new StoreCommerceProfile();
        profile.setPublishInventory(true);

        RetailStore store = new RetailStore();
        store.setId(storeId);
        store.setOrganizationId(orgId);
        store.setWarehouseId(warehouseId);

        CommerceOrderLine line = new CommerceOrderLine();
        line.setProductId(productId);
        line.setQuantity(BigDecimal.valueOf(2));

        when(storeProfiles.findByStoreId(storeId)).thenReturn(Optional.of(profile));
        when(stores.findByIdAndOrganizationIdAndDeletedFalse(storeId, orgId)).thenReturn(Optional.of(store));
        when(orderLines.findByOrderIdOrderByCreatedAtAsc(orderId)).thenReturn(List.of(line));
        when(sellableInventory.sellableQty(orgId, storeId, productId)).thenReturn(BigDecimal.TEN);

        service.syncOrder(order, null);

        verify(marketplaceSync).syncInventory(eq(storeId), eq(productId), eq(BigDecimal.TEN), eq(null));
    }

    @Test
    void syncOrderSkipsWhenInventoryPublishingDisabled() {
        UUID storeId = UUID.randomUUID();
        CommerceOrder order = new CommerceOrder();
        order.setStoreId(storeId);

        StoreCommerceProfile profile = new StoreCommerceProfile();
        profile.setPublishInventory(false);
        when(storeProfiles.findByStoreId(storeId)).thenReturn(Optional.of(profile));

        service.syncOrder(order, null);

        verify(marketplaceSync, never()).syncInventory(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
