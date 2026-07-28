package com.flowledger.commerce.order.erp;

import com.flowledger.commerce.cart.entity.CommerceCartItem;
import com.flowledger.commerce.cart.repository.CommerceCartItemRepository;
import com.flowledger.commerce.checkout.entity.CommerceCheckoutSession;
import com.flowledger.commerce.checkout.repository.CommerceCheckoutSessionRepository;
import com.flowledger.commerce.common.CommerceTenantScope;
import com.flowledger.commerce.order.CommerceOrderBuilder;
import com.flowledger.commerce.order.entity.CommerceOrder;
import com.flowledger.commerce.order.repository.CommerceOrderRepository;
import com.flowledger.inventory.service.InventoryService;
import com.flowledger.retail.entity.RetailStore;
import com.flowledger.retail.repository.RetailStoreRepository;
import com.flowledger.sales.dto.SalesDtos;
import com.flowledger.sales.service.SalesInvoiceService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PickupErpDocumentStrategy implements ErpDocumentStrategy {
    private final CommerceCheckoutSessionRepository checkoutSessions;
    private final CommerceCartItemRepository cartItems;
    private final CommerceOrderBuilder orderBuilder;
    private final SalesInvoiceService salesInvoiceService;
    private final InventoryService inventoryService;
    private final RetailStoreRepository stores;
    private final CommerceOrderRepository orders;

    public PickupErpDocumentStrategy(
            CommerceCheckoutSessionRepository checkoutSessions,
            CommerceCartItemRepository cartItems,
            CommerceOrderBuilder orderBuilder,
            SalesInvoiceService salesInvoiceService,
            InventoryService inventoryService,
            RetailStoreRepository stores,
            CommerceOrderRepository orders) {
        this.checkoutSessions = checkoutSessions;
        this.cartItems = cartItems;
        this.orderBuilder = orderBuilder;
        this.salesInvoiceService = salesInvoiceService;
        this.inventoryService = inventoryService;
        this.stores = stores;
        this.orders = orders;
    }

    @Override
    public ErpDocumentResult fulfillAtMilestone(
            CommerceCheckoutSession session, CommerceOrder order, UUID erpCustomerId, ErpMilestone milestone) {
        if (milestone != ErpMilestone.PICKED_UP
                && milestone != ErpMilestone.QR_VERIFIED
                && milestone != ErpMilestone.SCAN_EXIT_VERIFIED) {
            return new ErpDocumentResult(order.getErpSalesOrderId(), order.getErpInvoiceId());
        }
        if (order.getErpInvoiceId() != null) {
            return new ErpDocumentResult(null, order.getErpInvoiceId());
        }
        return CommerceTenantScope.run(session.getOrganizationId(), () -> {
            List<CommerceCartItem> items = cartItems.findByCartIdOrderByCreatedAtAsc(session.getCartId());
            RetailStore store = stores
                    .findByIdAndOrganizationIdAndDeletedFalse(session.getStoreId(), session.getOrganizationId())
                    .orElseThrow();

            SalesDtos.Invoice invoice = new SalesDtos.Invoice(
                    erpCustomerId,
                    LocalDate.now(),
                    null,
                    store.getWarehouseId(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    null,
                    null,
                    null,
                    "Commerce order " + order.getOrderNumber(),
                    null,
                    null,
                    orderBuilder.buildInvoiceItems(session.getOrganizationId(), items));

            SalesDtos.InvoiceDetail draft = salesInvoiceService.createDraft(invoice);
            SalesDtos.InvoiceDetail confirmed = salesInvoiceService.confirmConvertedForPos(draft.id());

            for (CommerceCartItem line : items) {
                inventoryService.postPosSale(
                        store.getWarehouseId(),
                        line.getProductId(),
                        line.getQuantity(),
                        null,
                        LocalDate.now(),
                        confirmed.id(),
                        confirmed.invoiceNumber(),
                        "commerce:" + order.getId() + ":" + line.getId());
            }
            salesInvoiceService.markInventoryPosted(confirmed.id());
            order.setErpInvoiceId(confirmed.id());
            orders.save(order);
            return new ErpDocumentResult(null, confirmed.id());
        });
    }
}
