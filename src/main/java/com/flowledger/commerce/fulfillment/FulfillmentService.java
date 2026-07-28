package com.flowledger.commerce.fulfillment;

import com.flowledger.commerce.auth.CommerceSecurityContext;
import com.flowledger.commerce.common.CommerceTenantScope;
import com.flowledger.commerce.dto.CommerceDtos;
import com.flowledger.commerce.fulfillment.FulfillmentType;
import com.flowledger.commerce.fulfillment.delivery.domain.DeliveryAssignmentStatus;
import com.flowledger.commerce.fulfillment.delivery.entity.DeliveryAssignment;
import com.flowledger.commerce.fulfillment.delivery.repository.DeliveryAssignmentRepository;
import com.flowledger.commerce.fulfillment.engine.FulfillmentOrchestrator;
import com.flowledger.commerce.fulfillment.order.domain.FulfillmentOrderStatus;
import com.flowledger.commerce.fulfillment.order.entity.FulfillmentOrder;
import com.flowledger.commerce.fulfillment.order.entity.FulfillmentStatusHistory;
import com.flowledger.commerce.fulfillment.order.repository.FulfillmentOrderRepository;
import com.flowledger.commerce.fulfillment.order.repository.FulfillmentStatusHistoryRepository;
import com.flowledger.commerce.fulfillment.pickup.entity.PickupSession;
import com.flowledger.commerce.fulfillment.pickup.repository.PickupSessionRepository;
import com.flowledger.commerce.fulfillment.strategy.FulfillmentStrategyRegistry;
import com.flowledger.commerce.fulfillment.task.TaskManagementService;
import com.flowledger.commerce.fulfillment.task.entity.PackingTask;
import com.flowledger.commerce.fulfillment.task.entity.PickingTask;
import com.flowledger.commerce.fulfillment.verification.QrTokenService;
import com.flowledger.commerce.order.CommerceOrderReturnService;
import com.flowledger.commerce.order.OrderMapper;
import com.flowledger.commerce.order.entity.CommerceOrder;
import com.flowledger.commerce.order.entity.CommerceOrderLine;
import com.flowledger.commerce.order.repository.CommerceOrderLineRepository;
import com.flowledger.commerce.order.repository.CommerceOrderRepository;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.common.security.SecurityUtils;
import com.flowledger.sales.repository.SalesInvoiceRepository;
import com.flowledger.sales.repository.SalesOrderRepository;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FulfillmentService {
    private final FulfillmentOrderRepository fulfillmentOrders;
    private final FulfillmentStatusHistoryRepository statusHistory;
    private final FulfillmentOrchestrator orchestrator;
    private final CommerceOrderRepository commerceOrders;
    private final DeliveryAssignmentRepository deliveryAssignments;
    private final PickupSessionRepository pickupSessions;
    private final QrTokenService qrTokens;
    private final FulfillmentStrategyRegistry strategyRegistry;
    private final TaskManagementService tasks;
    private final OrderMapper orderMapper;
    private final CommerceOrderLineRepository orderLines;
    private final CommerceOrderReturnService orderReturns;
    private final SalesOrderRepository salesOrders;
    private final SalesInvoiceRepository salesInvoices;

    public FulfillmentService(
            FulfillmentOrderRepository fulfillmentOrders,
            FulfillmentStatusHistoryRepository statusHistory,
            FulfillmentOrchestrator orchestrator,
            CommerceOrderRepository commerceOrders,
            DeliveryAssignmentRepository deliveryAssignments,
            PickupSessionRepository pickupSessions,
            QrTokenService qrTokens,
            FulfillmentStrategyRegistry strategyRegistry,
            TaskManagementService tasks,
            OrderMapper orderMapper,
            CommerceOrderLineRepository orderLines,
            CommerceOrderReturnService orderReturns,
            SalesOrderRepository salesOrders,
            SalesInvoiceRepository salesInvoices) {
        this.fulfillmentOrders = fulfillmentOrders;
        this.statusHistory = statusHistory;
        this.orchestrator = orchestrator;
        this.commerceOrders = commerceOrders;
        this.deliveryAssignments = deliveryAssignments;
        this.pickupSessions = pickupSessions;
        this.qrTokens = qrTokens;
        this.strategyRegistry = strategyRegistry;
        this.tasks = tasks;
        this.orderMapper = orderMapper;
        this.orderLines = orderLines;
        this.orderReturns = orderReturns;
        this.salesOrders = salesOrders;
        this.salesInvoices = salesInvoices;
    }

    public List<CommerceDtos.FulfillmentOrderResponse> listByStore(
            UUID storeId, String statusFilter, String fulfillmentTypesFilter) {
        List<FulfillmentOrder> orders;
        if (statusFilter != null && !statusFilter.isBlank()) {
            List<FulfillmentOrderStatus> statuses = Arrays.stream(statusFilter.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(FulfillmentOrderStatus::valueOf)
                    .toList();
            if (statuses.size() == 1 && statuses.get(0) == FulfillmentOrderStatus.COMPLETED) {
                orders = fulfillmentOrders.findByStoreIdAndStatusOrderByCompletedAtDesc(
                        storeId, FulfillmentOrderStatus.COMPLETED);
            } else {
                orders = fulfillmentOrders.findByStoreIdAndStatusIn(storeId, statuses);
            }
        } else {
            orders = fulfillmentOrders.findByStoreIdOrderByCreatedAtDesc(storeId);
        }
        if (fulfillmentTypesFilter != null && !fulfillmentTypesFilter.isBlank()) {
            var allowed = Arrays.stream(fulfillmentTypesFilter.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(FulfillmentType::valueOf)
                    .collect(Collectors.toSet());
            orders = orders.stream().filter(o -> allowed.contains(o.getFulfillmentType())).toList();
        }
        return orders.stream().map(this::toResponse).toList();
    }

    public CommerceDtos.FulfillmentDashboardResponse dashboard(UUID storeId) {
        return new CommerceDtos.FulfillmentDashboardResponse(
                fulfillmentOrders.countByStoreIdAndStatus(storeId, FulfillmentOrderStatus.CREATED),
                fulfillmentOrders.countByStoreIdAndStatus(storeId, FulfillmentOrderStatus.PICKING),
                fulfillmentOrders.countByStoreIdAndStatus(storeId, FulfillmentOrderStatus.PACKING),
                fulfillmentOrders.countByStoreIdAndStatus(storeId, FulfillmentOrderStatus.READY),
                deliveryAssignments
                        .findByStoreIdAndStatusIn(
                                storeId,
                                List.of(
                                        DeliveryAssignmentStatus.PENDING,
                                        DeliveryAssignmentStatus.ASSIGNED,
                                        DeliveryAssignmentStatus.DISPATCHED))
                        .size(),
                pickupSessions.findByStoreId(storeId).size(),
                fulfillmentOrders.countByStoreIdAndStatus(storeId, FulfillmentOrderStatus.COMPLETED));
    }

    @Transactional(readOnly = true)
    public CommerceDtos.FulfillmentOrderDetailResponse getDetail(UUID fulfillmentOrderId) {
        FulfillmentOrder order = requireFulfillment(fulfillmentOrderId);
        CommerceOrder commerceOrder = commerceOrders
                .findById(order.getCommerceOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Commerce order not found"));
        CommerceDtos.CommerceOrderResponse orderResponse = orderMapper.toOrderResponse(commerceOrder);
        Map<UUID, CommerceDtos.CommerceOrderLineResponse> lineById = orderResponse.lines().stream()
                .collect(Collectors.toMap(CommerceDtos.CommerceOrderLineResponse::id, Function.identity()));
        List<CommerceOrderLine> lines = orderLines.findByOrderIdOrderByCreatedAtAsc(commerceOrder.getId());
        List<CommerceDtos.FulfillmentOrderLineResponse> lineResponses = lines.stream()
                .map(line -> {
                    CommerceDtos.CommerceOrderLineResponse mapped = lineById.get(line.getId());
                    return new CommerceDtos.FulfillmentOrderLineResponse(
                            line.getId(),
                            line.getProductId(),
                            line.getQuantity(),
                            orderReturns.returnableQty(line.getId(), line.getQuantity()),
                            line.getLineSubtotal(),
                            line.getLineTax(),
                            line.getLineTotal(),
                            mapped != null ? mapped.name() : null,
                            mapped != null ? mapped.sku() : null);
                })
                .toList();
        UUID organizationId = order.getOrganizationId();
        UUID erpSalesOrderId = commerceOrder.getErpSalesOrderId();
        UUID erpInvoiceId = commerceOrder.getErpInvoiceId();
        String erpSalesOrderNumber = erpSalesOrderId == null
                ? null
                : CommerceTenantScope.run(organizationId, () -> salesOrders
                        .findByIdAndOrganizationId(erpSalesOrderId, organizationId)
                        .map(so -> so.getOrderNumber())
                        .orElse(null));
        String erpInvoiceNumber = erpInvoiceId == null
                ? null
                : CommerceTenantScope.run(organizationId, () -> salesInvoices
                        .findByIdAndOrganizationId(erpInvoiceId, organizationId)
                        .map(inv -> inv.getInvoiceNumber())
                        .orElse(null));
        return new CommerceDtos.FulfillmentOrderDetailResponse(
                order.getId(),
                order.getCommerceOrderId(),
                commerceOrder.getOrderNumber(),
                order.getStoreId(),
                order.getCustomerId(),
                order.getFulfillmentType().name(),
                order.getStatus().name(),
                order.getSubStatus() != null ? order.getSubStatus().name() : null,
                order.getAcceptedAt(),
                order.getReadyAt(),
                order.getCompletedAt(),
                commerceOrder.getCurrency(),
                commerceOrder.getGrandTotal(),
                erpSalesOrderId,
                erpSalesOrderNumber,
                erpInvoiceId,
                erpInvoiceNumber,
                lineResponses);
    }

    public CommerceDtos.CommerceOrderReturnResponse processReturn(
            UUID fulfillmentOrderId, CommerceDtos.CreateCommerceReturnRequest request) {
        return orderReturns.processReturn(fulfillmentOrderId, request);
    }

    public CommerceDtos.FulfillmentOrderResponse accept(CommerceDtos.AcceptFulfillmentRequest request) {
        UUID actorId = SecurityUtils.currentUserId();
        FulfillmentOrder order = orchestrator.accept(request.fulfillmentOrderId(), actorId);
        return toResponse(order);
    }

    public CommerceDtos.FulfillmentOrderResponse startPicking(CommerceDtos.FulfillmentTaskRequest request) {
        UUID actorId = request.staffId() != null ? request.staffId() : SecurityUtils.currentUserId();
        orchestrator.startPicking(request.fulfillmentOrderId(), actorId);
        return toResponse(requireFulfillment(request.fulfillmentOrderId()));
    }

    public CommerceDtos.FulfillmentOrderResponse completePicking(UUID fulfillmentOrderId) {
        UUID actorId = SecurityUtils.currentUserId();
        orchestrator.completePicking(fulfillmentOrderId, actorId);
        return toResponse(requireFulfillment(fulfillmentOrderId));
    }

    public CommerceDtos.FulfillmentOrderResponse startPacking(CommerceDtos.FulfillmentTaskRequest request) {
        UUID actorId = request.staffId() != null ? request.staffId() : SecurityUtils.currentUserId();
        orchestrator.startPacking(request.fulfillmentOrderId(), actorId);
        return toResponse(requireFulfillment(request.fulfillmentOrderId()));
    }

    public CommerceDtos.FulfillmentOrderResponse completePacking(UUID fulfillmentOrderId) {
        UUID actorId = SecurityUtils.currentUserId();
        orchestrator.completePacking(fulfillmentOrderId, actorId);
        return toResponse(requireFulfillment(fulfillmentOrderId));
    }

    public CommerceDtos.FulfillmentOrderResponse customerArrived(UUID fulfillmentOrderId) {
        UUID actorId = SecurityUtils.currentUserId();
        orchestrator.customerArrived(fulfillmentOrderId, actorId);
        return toResponse(requireFulfillment(fulfillmentOrderId));
    }

    public CommerceDtos.FulfillmentOrderResponse verifyPickup(UUID fulfillmentOrderId) {
        UUID actorId = SecurityUtils.currentUserId();
        orchestrator.verifyPickup(fulfillmentOrderId, actorId);
        return toResponse(requireFulfillment(fulfillmentOrderId));
    }

    public CommerceDtos.FulfillmentOrderResponse verifyCollect(CommerceDtos.VerifyCollectRequest request) {
        UUID actorId = SecurityUtils.currentUserId();
        FulfillmentOrder order = orchestrator.verifyCollectQr(request.token(), actorId);
        return toResponse(order);
    }

    public CommerceDtos.FulfillmentOrderResponse assignDriver(CommerceDtos.AssignDriverRequest request) {
        UUID actorId = SecurityUtils.currentUserId();
        orchestrator.assignDriver(request.fulfillmentOrderId(), request.driverId(), actorId);
        return toResponse(requireFulfillment(request.fulfillmentOrderId()));
    }

    public CommerceDtos.FulfillmentOrderResponse dispatchDelivery(UUID fulfillmentOrderId) {
        UUID actorId = SecurityUtils.currentUserId();
        orchestrator.dispatchDelivery(fulfillmentOrderId, actorId);
        return toResponse(requireFulfillment(fulfillmentOrderId));
    }

    public CommerceDtos.FulfillmentOrderResponse markDelivered(UUID fulfillmentOrderId) {
        UUID actorId = SecurityUtils.currentUserId();
        orchestrator.markDelivered(fulfillmentOrderId, actorId);
        return toResponse(requireFulfillment(fulfillmentOrderId));
    }

    public CommerceDtos.OrderTrackingResponse getTracking(UUID orderId) {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        CommerceOrder order = commerceOrders
                .findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        FulfillmentOrder fulfillment = fulfillmentOrders
                .findByCommerceOrderId(order.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Fulfillment not found"));

        List<String> milestones = strategyRegistry.require(fulfillment.getFulfillmentType()).milestoneLabels();
        List<FulfillmentStatusHistory> history =
                statusHistory.findByFulfillmentOrderIdOrderByCreatedAtAsc(fulfillment.getId());
        List<CommerceDtos.TrackingMilestoneResponse> timeline = new ArrayList<>();
        for (int i = 0; i < milestones.size(); i++) {
            boolean reached = i < history.size();
            timeline.add(new CommerceDtos.TrackingMilestoneResponse(
                    milestones.get(i), reached, reached && i < history.size() ? history.get(i).getCreatedAt() : null));
        }
        return new CommerceDtos.OrderTrackingResponse(
                order.getId(),
                fulfillment.getId(),
                fulfillment.getStatus().name(),
                fulfillment.getSubStatus() != null ? fulfillment.getSubStatus().name() : null,
                timeline);
    }

    public CommerceDtos.PickupQrResponse getPickupQr(UUID orderId) {
        UUID customerId = CommerceSecurityContext.currentCustomer().getCustomerId();
        CommerceOrder order = commerceOrders
                .findByIdAndCustomerId(orderId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        FulfillmentOrder fulfillment = fulfillmentOrders
                .findByCommerceOrderId(order.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Fulfillment not found"));
        if (fulfillment.getStatus() != FulfillmentOrderStatus.READY
                && fulfillment.getStatus() != FulfillmentOrderStatus.FULFILLING) {
            throw new BusinessException("Pickup QR not available yet");
        }
        String collectCode = qrTokens.getOrIssueCollectCode(fulfillment.getId());
        return new CommerceDtos.PickupQrResponse(orderId, fulfillment.getId(), collectCode);
    }

    public List<PickingTask> listPickingQueue(UUID storeId) {
        return tasks.listOpenPicking(storeId);
    }

    public List<PackingTask> listPackingQueue(UUID storeId) {
        return tasks.listOpenPacking(storeId);
    }

    public List<DeliveryAssignment> listDeliveryQueue(UUID storeId) {
        return deliveryAssignments.findByStoreIdAndStatusIn(
                storeId,
                List.of(
                        DeliveryAssignmentStatus.PENDING,
                        DeliveryAssignmentStatus.ASSIGNED,
                        DeliveryAssignmentStatus.DISPATCHED));
    }

    private FulfillmentOrder requireFulfillment(UUID id) {
        return fulfillmentOrders.findById(id).orElseThrow(() -> new ResourceNotFoundException("Fulfillment order not found"));
    }

    private CommerceDtos.FulfillmentOrderResponse toResponse(FulfillmentOrder order) {
        CommerceOrder commerceOrder = commerceOrders
                .findById(order.getCommerceOrderId())
                .orElseThrow();
        return new CommerceDtos.FulfillmentOrderResponse(
                order.getId(),
                order.getCommerceOrderId(),
                commerceOrder.getOrderNumber(),
                order.getStoreId(),
                order.getCustomerId(),
                order.getFulfillmentType().name(),
                order.getStatus().name(),
                order.getSubStatus() != null ? order.getSubStatus().name() : null,
                order.getAcceptedAt(),
                order.getReadyAt(),
                order.getCompletedAt());
    }
}
