package com.flowledger.commerce.order.fulfillment;

import com.flowledger.commerce.cart.entity.CommerceCartItem;
import com.flowledger.commerce.cart.repository.CommerceCartItemRepository;
import com.flowledger.commerce.checkout.entity.CommerceCheckoutSession;
import com.flowledger.commerce.common.CommerceTenantScope;
import com.flowledger.commerce.order.CommerceOrderBuilder;
import com.flowledger.commerce.order.entity.CommerceOrder;
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
public class DeliveryFulfillmentStrategy implements FulfillmentStrategy {
    private final CommerceCartItemRepository cartItems;
    private final CommerceOrderBuilder orderBuilder;
    private final SalesDocumentService salesDocuments;
    private final SalesInvoiceService salesInvoiceService;
    private final RetailStoreRepository stores;

    public DeliveryFulfillmentStrategy(
            CommerceCartItemRepository cartItems,
            CommerceOrderBuilder orderBuilder,
            SalesDocumentService salesDocuments,
            SalesInvoiceService salesInvoiceService,
            RetailStoreRepository stores) {
        this.cartItems = cartItems;
        this.orderBuilder = orderBuilder;
        this.salesDocuments = salesDocuments;
        this.salesInvoiceService = salesInvoiceService;
        this.stores = stores;
    }

    @Override
    public FulfillmentResult fulfill(CommerceCheckoutSession session, CommerceOrder order, UUID erpCustomerId) {
        return CommerceTenantScope.run(session.getOrganizationId(), () -> {
            List<CommerceCartItem> items = cartItems.findByCartIdOrderByCreatedAtAsc(session.getCartId());
            RetailStore store = stores
                    .findByIdAndOrganizationIdAndDeletedFalse(session.getStoreId(), session.getOrganizationId())
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
                    orderBuilder.buildInvoiceItems(session.getOrganizationId(), items));

            SalesOrder salesOrder = salesDocuments.createOrder(orderRequest);
            salesDocuments.confirmOrder(salesOrder.getId(), new ConfirmOrderRequest(store.getWarehouseId()));
            SalesInvoice invoice = salesDocuments.convertOrderToInvoice(salesOrder.getId(), store.getWarehouseId());
            salesInvoiceService.confirmConverted(invoice.getId());
            return new FulfillmentResult(salesOrder.getId(), invoice.getId());
        });
    }
}
