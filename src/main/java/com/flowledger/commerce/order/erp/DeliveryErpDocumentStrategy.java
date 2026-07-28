package com.flowledger.commerce.order.erp;

import com.flowledger.commerce.cart.entity.CommerceCartItem;
import com.flowledger.commerce.cart.repository.CommerceCartItemRepository;
import com.flowledger.commerce.checkout.entity.CommerceCheckoutSession;
import com.flowledger.commerce.common.CommerceOrganizations;
import com.flowledger.commerce.common.CommerceTenantScope;
import com.flowledger.commerce.marketplace.CommerceMarketplaceInventorySyncService;
import com.flowledger.commerce.order.CommerceOrderBuilder;
import com.flowledger.commerce.order.entity.CommerceOrder;
import com.flowledger.commerce.order.repository.CommerceOrderRepository;
import com.flowledger.retail.entity.RetailStore;
import com.flowledger.retail.repository.RetailStoreRepository;
import com.flowledger.sales.dto.SalesAllocationDtos.ConfirmOrderRequest;
import com.flowledger.sales.dto.SalesDtos;
import com.flowledger.sales.entity.SalesInvoice;
import com.flowledger.sales.entity.SalesOrder;
import com.flowledger.sales.service.SalesDocumentService;
import com.flowledger.sales.service.SalesInvoiceService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DeliveryErpDocumentStrategy implements ErpDocumentStrategy {
    private final CommerceCartItemRepository cartItems;
    private final CommerceOrderBuilder orderBuilder;
    private final SalesDocumentService salesDocuments;
    private final SalesInvoiceService salesInvoiceService;
    private final RetailStoreRepository stores;
    private final CommerceOrderRepository orders;
    private final CommerceMarketplaceInventorySyncService marketplaceInventorySync;

    public DeliveryErpDocumentStrategy(
            CommerceCartItemRepository cartItems,
            CommerceOrderBuilder orderBuilder,
            SalesDocumentService salesDocuments,
            SalesInvoiceService salesInvoiceService,
            RetailStoreRepository stores,
            CommerceOrderRepository orders,
            CommerceMarketplaceInventorySyncService marketplaceInventorySync) {
        this.cartItems = cartItems;
        this.orderBuilder = orderBuilder;
        this.salesDocuments = salesDocuments;
        this.salesInvoiceService = salesInvoiceService;
        this.stores = stores;
        this.orders = orders;
        this.marketplaceInventorySync = marketplaceInventorySync;
    }

    @Override
    public ErpDocumentResult fulfillAtMilestone(
            CommerceCheckoutSession session, CommerceOrder order, UUID erpCustomerId, ErpMilestone milestone) {
        UUID organizationId = CommerceOrganizations.resolve(order, session);
        return CommerceTenantScope.run(organizationId, () -> {
            if (milestone == ErpMilestone.ACCEPTED && order.getErpSalesOrderId() == null) {
                List<CommerceCartItem> items = cartItems.findByCartIdOrderByCreatedAtAsc(session.getCartId());
                RetailStore store = stores
                        .findByIdAndOrganizationIdAndDeletedFalse(session.getStoreId(), organizationId)
                        .orElseThrow();
                SalesDtos.OrderRequest orderRequest = new SalesDtos.OrderRequest(
                        erpCustomerId,
                        LocalDate.now(),
                        null,
                        null,
                        null,
                        null,
                        store.getState(),
                        "Commerce delivery " + order.getOrderNumber(),
                        null,
                        orderBuilder.buildInvoiceItems(organizationId, items));
                SalesOrder salesOrder = salesDocuments.createOrder(orderRequest);
                salesDocuments.confirmOrder(salesOrder.getId(), new ConfirmOrderRequest(store.getWarehouseId()));
                order.setErpSalesOrderId(salesOrder.getId());
                orders.save(order);
                return new ErpDocumentResult(salesOrder.getId(), order.getErpInvoiceId());
            }
            if (milestone == ErpMilestone.OUT_FOR_DELIVERY && order.getErpInvoiceId() == null) {
                RetailStore store = stores
                        .findByIdAndOrganizationIdAndDeletedFalse(session.getStoreId(), organizationId)
                        .orElseThrow();
                SalesInvoice invoice =
                        salesDocuments.convertOrderToInvoice(order.getErpSalesOrderId(), store.getWarehouseId());
                salesInvoiceService.confirmConverted(invoice.getId());
                order.setErpInvoiceId(invoice.getId());
                orders.save(order);
                marketplaceInventorySync.syncOrder(order, null);
                return new ErpDocumentResult(order.getErpSalesOrderId(), invoice.getId());
            }
            if (milestone == ErpMilestone.OUT_FOR_DELIVERY) {
                marketplaceInventorySync.syncOrder(order, null);
            }
            return new ErpDocumentResult(order.getErpSalesOrderId(), order.getErpInvoiceId());
        });
    }
}
