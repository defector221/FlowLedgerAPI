package com.flowledger.commerce.order;

import com.flowledger.commerce.common.CommerceTenantScope;
import com.flowledger.commerce.dto.CommerceDtos;
import com.flowledger.commerce.fulfillment.order.domain.FulfillmentOrderStatus;
import com.flowledger.commerce.fulfillment.order.entity.FulfillmentOrder;
import com.flowledger.commerce.fulfillment.order.repository.FulfillmentOrderRepository;
import com.flowledger.commerce.marketplace.CommerceMarketplaceInventorySyncService;
import com.flowledger.commerce.order.domain.CommerceOrderReturnStatus;
import com.flowledger.commerce.order.domain.CommerceOrderStatus;
import com.flowledger.commerce.order.entity.CommerceOrder;
import com.flowledger.commerce.order.entity.CommerceOrderLine;
import com.flowledger.commerce.order.entity.CommerceOrderReturn;
import com.flowledger.commerce.order.entity.CommerceOrderReturnLine;
import com.flowledger.commerce.order.repository.CommerceOrderLineRepository;
import com.flowledger.commerce.order.repository.CommerceOrderRepository;
import com.flowledger.commerce.order.repository.CommerceOrderReturnRepository;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.common.security.SecurityUtils;
import com.flowledger.sales.dto.SalesDtos;
import com.flowledger.sales.entity.SalesReturn;
import com.flowledger.sales.service.SalesDocumentService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class CommerceOrderReturnService {
    private final FulfillmentOrderRepository fulfillmentOrders;
    private final CommerceOrderRepository orders;
    private final CommerceOrderLineRepository orderLines;
    private final CommerceOrderReturnRepository returns;
    private final SalesDocumentService salesDocuments;
    private final CommerceMarketplaceInventorySyncService marketplaceInventorySync;

    public CommerceOrderReturnService(
            FulfillmentOrderRepository fulfillmentOrders,
            CommerceOrderRepository orders,
            CommerceOrderLineRepository orderLines,
            CommerceOrderReturnRepository returns,
            SalesDocumentService salesDocuments,
            CommerceMarketplaceInventorySyncService marketplaceInventorySync) {
        this.fulfillmentOrders = fulfillmentOrders;
        this.orders = orders;
        this.orderLines = orderLines;
        this.returns = returns;
        this.salesDocuments = salesDocuments;
        this.marketplaceInventorySync = marketplaceInventorySync;
    }

    @Transactional(readOnly = true)
    public BigDecimal returnableQty(UUID orderLineId, BigDecimal orderedQty) {
        BigDecimal returned = returns.sumReturnedQtyByOrderLineId(orderLineId);
        if (returned == null) {
            returned = BigDecimal.ZERO;
        }
        return orderedQty.subtract(returned).max(BigDecimal.ZERO);
    }

    public CommerceDtos.CommerceOrderReturnResponse processReturn(
            UUID fulfillmentOrderId, CommerceDtos.CreateCommerceReturnRequest request) {
        FulfillmentOrder fulfillment = fulfillmentOrders
                .findById(fulfillmentOrderId)
                .orElseThrow(() -> new ResourceNotFoundException("Fulfillment order not found"));
        if (fulfillment.getStatus() != FulfillmentOrderStatus.COMPLETED) {
            throw new BusinessException("Returns are allowed only for completed orders");
        }

        CommerceOrder order = orders
                .findById(fulfillment.getCommerceOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Commerce order not found"));
        if (order.getStatus() != CommerceOrderStatus.COMPLETED) {
            throw new BusinessException("Commerce order is not completed");
        }
        if (order.getErpInvoiceId() == null) {
            throw new BusinessException("No ERP invoice linked to this order — cannot process return");
        }

        List<CommerceOrderLine> orderLineEntities =
                orderLines.findByOrderIdOrderByCreatedAtAsc(order.getId());
        Map<UUID, CommerceOrderLine> linesById =
                orderLineEntities.stream().collect(Collectors.toMap(CommerceOrderLine::getId, Function.identity()));

        List<SalesDtos.ReturnItem> erpItems = new ArrayList<>();
        Set<UUID> productIds = new HashSet<>();
        for (CommerceDtos.CommerceReturnLineRequest lineReq : request.lines()) {
            CommerceOrderLine line = linesById.get(lineReq.orderLineId());
            if (line == null) {
                throw new BusinessException("Order line not found: " + lineReq.orderLineId());
            }
            BigDecimal returnable = returnableQty(line.getId(), line.getQuantity());
            if (lineReq.quantity().compareTo(returnable) > 0) {
                throw new BusinessException(
                        "Return quantity exceeds returnable amount for line " + line.getId());
            }
            if (lineReq.quantity().signum() <= 0) {
                continue;
            }
            BigDecimal rate = line.getLineSubtotal()
                    .divide(line.getQuantity(), 4, RoundingMode.HALF_UP);
            erpItems.add(new SalesDtos.ReturnItem(line.getProductId(), lineReq.quantity(), rate));
            productIds.add(line.getProductId());
        }
        if (erpItems.isEmpty()) {
            throw new BusinessException("Select at least one item to return");
        }

        UUID actorId = SecurityUtils.currentUserId();
        UUID organizationId = order.getOrganizationId();

        SalesReturn confirmed = CommerceTenantScope.run(organizationId, () -> {
            SalesReturn draft = salesDocuments.createReturn(new SalesDtos.ReturnRequest(
                    order.getErpInvoiceId(),
                    request.returnDate() != null ? request.returnDate() : LocalDate.now(),
                    request.notes(),
                    erpItems));
            return salesDocuments.confirmReturn(draft.getId());
        });

        CommerceOrderReturn commerceReturn = new CommerceOrderReturn();
        commerceReturn.setOrganizationId(organizationId);
        commerceReturn.setCommerceOrderId(order.getId());
        commerceReturn.setFulfillmentOrderId(fulfillmentOrderId);
        commerceReturn.setErpSalesReturnId(confirmed.getId());
        commerceReturn.setReturnNumber(confirmed.getReturnNumber());
        commerceReturn.setStatus(CommerceOrderReturnStatus.CONFIRMED);
        commerceReturn.setNotes(request.notes());

        for (CommerceDtos.CommerceReturnLineRequest lineReq : request.lines()) {
            if (lineReq.quantity().signum() <= 0) {
                continue;
            }
            CommerceOrderLine line = linesById.get(lineReq.orderLineId());
            BigDecimal rate = line.getLineSubtotal()
                    .divide(line.getQuantity(), 4, RoundingMode.HALF_UP);
            CommerceOrderReturnLine returnLine = new CommerceOrderReturnLine();
            returnLine.setOrderReturn(commerceReturn);
            returnLine.setOrderLineId(line.getId());
            returnLine.setProductId(line.getProductId());
            returnLine.setQuantity(lineReq.quantity());
            returnLine.setRate(rate);
            returnLine.setLineTotal(lineReq.quantity().multiply(rate).setScale(4, RoundingMode.HALF_UP));
            commerceReturn.getLines().add(returnLine);
        }
        commerceReturn = returns.save(commerceReturn);

        marketplaceInventorySync.syncOrder(order, actorId);

        return toResponse(commerceReturn);
    }

    private CommerceDtos.CommerceOrderReturnResponse toResponse(CommerceOrderReturn commerceReturn) {
        return new CommerceDtos.CommerceOrderReturnResponse(
                commerceReturn.getId(),
                commerceReturn.getCommerceOrderId(),
                commerceReturn.getFulfillmentOrderId(),
                commerceReturn.getErpSalesReturnId(),
                commerceReturn.getReturnNumber(),
                commerceReturn.getStatus().name(),
                commerceReturn.getLines().stream()
                        .map(line -> new CommerceDtos.CommerceReturnLineResponse(
                                line.getOrderLineId(),
                                line.getProductId(),
                                line.getQuantity(),
                                line.getRate(),
                                line.getLineTotal()))
                        .toList(),
                commerceReturn.getCreatedAt());
    }
}
