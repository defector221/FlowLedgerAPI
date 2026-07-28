package com.flowledger.commerce.fulfillment.engine;

import com.flowledger.commerce.cart.repository.CommerceCartRepository;
import com.flowledger.commerce.checkout.entity.CommerceCheckoutSession;
import com.flowledger.commerce.checkout.repository.CommerceCheckoutSessionRepository;
import com.flowledger.commerce.events.DeliveredEvent;
import com.flowledger.commerce.events.DeliveryAssignedEvent;
import com.flowledger.commerce.events.FulfillmentAcceptedEvent;
import com.flowledger.commerce.events.FulfillmentOrderCreatedEvent;
import com.flowledger.commerce.events.OutForDeliveryEvent;
import com.flowledger.commerce.events.PackingCompletedEvent;
import com.flowledger.commerce.events.PackingStartedEvent;
import com.flowledger.commerce.events.PickingCompletedEvent;
import com.flowledger.commerce.events.PickingStartedEvent;
import com.flowledger.commerce.events.PickupCompletedEvent;
import com.flowledger.commerce.events.CustomerArrivedEvent;
import com.flowledger.commerce.events.ReadyForPickupEvent;
import com.flowledger.commerce.fulfillment.delivery.domain.DeliveryAssignmentStatus;
import com.flowledger.commerce.fulfillment.delivery.entity.DeliveryAssignment;
import com.flowledger.commerce.fulfillment.delivery.repository.DeliveryAssignmentRepository;
import com.flowledger.commerce.fulfillment.notification.FulfillmentNotificationHooks;
import com.flowledger.commerce.fulfillment.FulfillmentType;
import com.flowledger.commerce.fulfillment.order.domain.FulfillmentOrderStatus;
import com.flowledger.commerce.fulfillment.order.domain.FulfillmentSubStatus;
import com.flowledger.commerce.fulfillment.order.entity.FulfillmentOrder;
import com.flowledger.commerce.fulfillment.order.entity.FulfillmentStatusHistory;
import com.flowledger.commerce.fulfillment.order.repository.FulfillmentOrderRepository;
import com.flowledger.commerce.fulfillment.order.repository.FulfillmentStatusHistoryRepository;
import com.flowledger.commerce.fulfillment.pickup.domain.PickupSessionStatus;
import com.flowledger.commerce.fulfillment.pickup.entity.PickupSession;
import com.flowledger.commerce.fulfillment.pickup.repository.PickupSessionRepository;
import com.flowledger.commerce.fulfillment.strategy.FulfillmentStrategy;
import com.flowledger.commerce.fulfillment.strategy.FulfillmentStrategyRegistry;
import com.flowledger.commerce.fulfillment.task.TaskManagementService;
import com.flowledger.commerce.fulfillment.verification.QrTokenService;
import com.flowledger.commerce.order.domain.CommerceOrderStatus;
import com.flowledger.commerce.order.entity.CommerceOrder;
import com.flowledger.commerce.order.entity.CommerceOrderLine;
import com.flowledger.commerce.order.erp.ErpDocumentService;
import com.flowledger.commerce.order.erp.ErpDocumentStrategy;
import com.flowledger.commerce.order.repository.CommerceOrderLineRepository;
import com.flowledger.commerce.order.repository.CommerceOrderRepository;
import com.flowledger.commerce.reservation.CommerceInventoryReservationService;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.exception.ResourceNotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FulfillmentOrchestrator {
    private final FulfillmentOrderRepository fulfillmentOrders;
    private final FulfillmentStatusHistoryRepository statusHistory;
    private final CommerceOrderRepository commerceOrders;
    private final CommerceOrderLineRepository orderLines;
    private final CommerceCheckoutSessionRepository checkoutSessions;
    private final CommerceCartRepository carts;
    private final FulfillmentStrategyRegistry strategyRegistry;
    private final StatusTransitionRules transitionRules;
    private final TaskManagementService tasks;
    private final ErpDocumentService erpDocuments;
    private final DeliveryAssignmentRepository deliveryAssignments;
    private final PickupSessionRepository pickupSessions;
    private final QrTokenService qrTokens;
    private final CommerceInventoryReservationService reservations;
    private final FulfillmentNotificationHooks notifications;
    private final com.flowledger.platform.event.bus.CommercePlatformEventBridge platformEvents;

    public FulfillmentOrchestrator(
            FulfillmentOrderRepository fulfillmentOrders,
            FulfillmentStatusHistoryRepository statusHistory,
            CommerceOrderRepository commerceOrders,
            CommerceOrderLineRepository orderLines,
            CommerceCheckoutSessionRepository checkoutSessions,
            CommerceCartRepository carts,
            FulfillmentStrategyRegistry strategyRegistry,
            StatusTransitionRules transitionRules,
            TaskManagementService tasks,
            ErpDocumentService erpDocuments,
            DeliveryAssignmentRepository deliveryAssignments,
            PickupSessionRepository pickupSessions,
            QrTokenService qrTokens,
            CommerceInventoryReservationService reservations,
            FulfillmentNotificationHooks notifications,
            com.flowledger.platform.event.bus.CommercePlatformEventBridge platformEvents) {
        this.fulfillmentOrders = fulfillmentOrders;
        this.statusHistory = statusHistory;
        this.commerceOrders = commerceOrders;
        this.orderLines = orderLines;
        this.checkoutSessions = checkoutSessions;
        this.carts = carts;
        this.strategyRegistry = strategyRegistry;
        this.transitionRules = transitionRules;
        this.tasks = tasks;
        this.erpDocuments = erpDocuments;
        this.deliveryAssignments = deliveryAssignments;
        this.pickupSessions = pickupSessions;
        this.qrTokens = qrTokens;
        this.reservations = reservations;
        this.notifications = notifications;
        this.platformEvents = platformEvents;
    }

    public FulfillmentOrder createFromOrder(CommerceOrder order, UUID actorId) {
        fulfillmentOrders.findByCommerceOrderId(order.getId()).ifPresent(existing -> {
            throw new IllegalStateException("Fulfillment order already exists");
        });

        FulfillmentOrder fulfillment = new FulfillmentOrder();
        fulfillment.setCommerceOrderId(order.getId());
        fulfillment.setOrganizationId(order.getOrganizationId());
        fulfillment.setStoreId(order.getStoreId());
        fulfillment.setCustomerId(order.getCustomerId());
        fulfillment.setFulfillmentType(order.getFulfillmentType());
        fulfillment.setStatus(FulfillmentOrderStatus.CREATED);
        fulfillment = fulfillmentOrders.save(fulfillment);
        recordHistory(fulfillment, null, fulfillment.getStatus(), fulfillment.getSubStatus(), actorId, "Created");
        notifications.publish(new FulfillmentOrderCreatedEvent(
                this, order.getOrganizationId(), actorId, fulfillment.getId()));
        return fulfillment;
    }

    public FulfillmentOrder accept(UUID fulfillmentOrderId, UUID actorId) {
        FulfillmentOrder order = requireFulfillment(fulfillmentOrderId);
        FulfillmentContext ctx = buildContext(order, actorId);
        transition(order, FulfillmentOrderStatus.ACCEPTED, FulfillmentSubStatus.NONE, actorId, "Accepted");
        order.setAcceptedAt(OffsetDateTime.now());
        fulfillmentOrders.save(order);

        FulfillmentStrategy strategy = strategyRegistry.require(order.getFulfillmentType());
        strategy.onAccepted(ctx);

        syncCommerceOrder(order, CommerceOrderStatus.IN_FULFILLMENT);
        notifications.publish(new FulfillmentAcceptedEvent(
                this, order.getOrganizationId(), actorId, order.getId()));

        if (strategy.requiresPicking()) {
            List<CommerceOrderLine> lines = orderLines.findByOrderIdOrderByCreatedAtAsc(order.getCommerceOrderId());
            tasks.createPickingTask(order, lines);
            transition(order, FulfillmentOrderStatus.PICKING, FulfillmentSubStatus.NONE, actorId, "Picking queued");
        } else if (order.getFulfillmentType() != com.flowledger.commerce.fulfillment.FulfillmentType.SCAN_AND_GO) {
            transition(order, FulfillmentOrderStatus.COMPLETED, FulfillmentSubStatus.NONE, actorId, "Auto completed");
            order.setCompletedAt(OffsetDateTime.now());
            fulfillmentOrders.save(order);
            strategy.onComplete(buildContext(order, actorId));
            syncCommerceOrder(order, CommerceOrderStatus.COMPLETED);
            releaseReservations(order);
        }
        return order;
    }

    public FulfillmentOrder fulfillScanAndGoExit(UUID fulfillmentOrderId, UUID actorId) {
        FulfillmentOrder order = requireFulfillment(fulfillmentOrderId);
        if (order.getStatus() == FulfillmentOrderStatus.CREATED) {
            transition(order, FulfillmentOrderStatus.ACCEPTED, FulfillmentSubStatus.NONE, actorId, "Scan exit accepted");
            order.setAcceptedAt(OffsetDateTime.now());
            fulfillmentOrders.save(order);
            syncCommerceOrder(order, CommerceOrderStatus.IN_FULFILLMENT);
        }
        return completeScanAndGo(fulfillmentOrderId, actorId);
    }

    public FulfillmentOrder startPicking(UUID fulfillmentOrderId, UUID pickerId) {
        FulfillmentOrder order = requireFulfillment(fulfillmentOrderId);
        assertStatus(order, FulfillmentOrderStatus.PICKING);
        tasks.startPicking(fulfillmentOrderId, pickerId);
        notifications.publish(new PickingStartedEvent(
                this, order.getOrganizationId(), pickerId, order.getId()));
        return order;
    }

    public FulfillmentOrder completePicking(UUID fulfillmentOrderId, UUID actorId) {
        FulfillmentOrder order = requireFulfillment(fulfillmentOrderId);
        assertStatus(order, FulfillmentOrderStatus.PICKING);
        tasks.completePicking(fulfillmentOrderId, actorId);
        transition(order, FulfillmentOrderStatus.PACKING, FulfillmentSubStatus.NONE, actorId, "Picking completed");
        notifications.publish(new PickingCompletedEvent(
                this, order.getOrganizationId(), actorId, order.getId()));
        return order;
    }

    public FulfillmentOrder startPacking(UUID fulfillmentOrderId, UUID packerId) {
        FulfillmentOrder order = requireFulfillment(fulfillmentOrderId);
        assertStatus(order, FulfillmentOrderStatus.PACKING);
        tasks.startPacking(fulfillmentOrderId, packerId);
        notifications.publish(new PackingStartedEvent(
                this, order.getOrganizationId(), packerId, order.getId()));
        return order;
    }

    public FulfillmentOrder completePacking(UUID fulfillmentOrderId, UUID actorId) {
        FulfillmentOrder order = requireFulfillment(fulfillmentOrderId);
        assertStatus(order, FulfillmentOrderStatus.PACKING);
        tasks.completePacking(fulfillmentOrderId, actorId);
        transition(order, FulfillmentOrderStatus.READY, FulfillmentSubStatus.NONE, actorId, "Ready for handoff");
        order.setReadyAt(OffsetDateTime.now());
        fulfillmentOrders.save(order);

        FulfillmentContext ctx = buildContext(order, actorId);
        strategyRegistry.require(order.getFulfillmentType()).onReady(ctx);
        notifications.publish(new PackingCompletedEvent(
                this, order.getOrganizationId(), actorId, order.getId()));
        notifications.publish(new ReadyForPickupEvent(
                this, order.getOrganizationId(), actorId, order.getId()));
        return order;
    }

    public FulfillmentOrder customerArrived(UUID fulfillmentOrderId, UUID actorId) {
        FulfillmentOrder order = requireFulfillment(fulfillmentOrderId);
        assertStatus(order, FulfillmentOrderStatus.READY);
        requirePickupFulfillment(order);
        PickupSession session = ensurePickupSession(order);
        session.setStatus(PickupSessionStatus.ARRIVED);
        session.setArrivedAt(OffsetDateTime.now());
        pickupSessions.save(session);

        transition(order, FulfillmentOrderStatus.FULFILLING, FulfillmentSubStatus.CUSTOMER_ARRIVED, actorId, "Customer arrived");
        notifications.publish(new CustomerArrivedEvent(
                this, order.getOrganizationId(), actorId, order.getId()));
        return order;
    }

    public FulfillmentOrder verifyPickup(UUID fulfillmentOrderId, UUID actorId) {
        FulfillmentOrder order = requireFulfillment(fulfillmentOrderId);
        requirePickupFulfillment(order);
        FulfillmentContext ctx = buildContext(order, actorId);
        erpDocuments.postAtMilestone(
                order.getFulfillmentType(),
                ctx.checkoutSession(),
                ctx.commerceOrder(),
                ctx.commerceOrder().getErpCustomerId(),
                ErpDocumentStrategy.ErpMilestone.PICKED_UP);

        PickupSession session = ensurePickupSession(order);
        session.setStatus(PickupSessionStatus.COLLECTED);
        session.setCollectedAt(OffsetDateTime.now());
        pickupSessions.save(session);

        order.setSubStatus(FulfillmentSubStatus.PICKED_UP);
        completeFulfillment(order, actorId, "Picked up");
        strategyRegistry.require(order.getFulfillmentType()).onComplete(ctx);
        notifications.publish(new PickupCompletedEvent(
                this, order.getOrganizationId(), actorId, order.getId()));
        return order;
    }

    public FulfillmentOrder verifyCollectQr(String token, UUID actorId) {
        UUID fulfillmentOrderId = qrTokens.verifyCollectToken(token);
        FulfillmentOrder order = requireFulfillment(fulfillmentOrderId);
        FulfillmentContext ctx = buildContext(order, actorId);
        erpDocuments.postAtMilestone(
                order.getFulfillmentType(),
                ctx.checkoutSession(),
                ctx.commerceOrder(),
                ctx.commerceOrder().getErpCustomerId(),
                ErpDocumentStrategy.ErpMilestone.QR_VERIFIED);

        order.setSubStatus(FulfillmentSubStatus.QR_VERIFIED);
        if (order.getStatus() == FulfillmentOrderStatus.READY) {
            transition(order, FulfillmentOrderStatus.FULFILLING, FulfillmentSubStatus.QR_VERIFIED, actorId, "QR verified");
        }
        completeFulfillment(order, actorId, "Collect verified");
        strategyRegistry.require(order.getFulfillmentType()).onComplete(ctx);
        notifications.publish(new PickupCompletedEvent(
                this, order.getOrganizationId(), actorId, order.getId()));
        return order;
    }

    public FulfillmentOrder assignDriver(UUID fulfillmentOrderId, UUID driverId, UUID actorId) {
        FulfillmentOrder order = requireFulfillment(fulfillmentOrderId);
        assertStatus(order, FulfillmentOrderStatus.READY);
        DeliveryAssignment assignment = deliveryAssignments
                .findByFulfillmentOrderId(fulfillmentOrderId)
                .orElseThrow(() -> new BusinessException("Delivery assignment not found"));
        assignment.setDriverId(driverId);
        assignment.setStatus(DeliveryAssignmentStatus.ASSIGNED);
        assignment.setAssignedAt(OffsetDateTime.now());
        deliveryAssignments.save(assignment);

        transition(order, FulfillmentOrderStatus.FULFILLING, FulfillmentSubStatus.DRIVER_ASSIGNED, actorId, "Driver assigned");
        notifications.publish(new DeliveryAssignedEvent(
                this, order.getOrganizationId(), actorId, order.getId()));
        return order;
    }

    public FulfillmentOrder dispatchDelivery(UUID fulfillmentOrderId, UUID actorId) {
        FulfillmentOrder order = requireFulfillment(fulfillmentOrderId);
        if (order.getFulfillmentType() != FulfillmentType.HOME_DELIVERY) {
            throw new BusinessException("Only home delivery orders can be dispatched");
        }
        if (order.getStatus() == FulfillmentOrderStatus.READY) {
            transition(order, FulfillmentOrderStatus.FULFILLING, FulfillmentSubStatus.NONE, actorId, "Dispatching delivery");
        } else if (order.getStatus() != FulfillmentOrderStatus.FULFILLING) {
            throw new BusinessException("Delivery order is not ready to dispatch");
        }
        FulfillmentContext ctx = buildContext(order, actorId);
        erpDocuments.postAtMilestone(
                order.getFulfillmentType(),
                ctx.checkoutSession(),
                ctx.commerceOrder(),
                ctx.commerceOrder().getErpCustomerId(),
                ErpDocumentStrategy.ErpMilestone.OUT_FOR_DELIVERY);

        DeliveryAssignment assignment = deliveryAssignments
                .findByFulfillmentOrderId(fulfillmentOrderId)
                .orElseThrow(() -> new BusinessException("Delivery assignment not found"));
        assignment.setStatus(DeliveryAssignmentStatus.DISPATCHED);
        assignment.setDispatchedAt(OffsetDateTime.now());
        deliveryAssignments.save(assignment);

        order.setSubStatus(FulfillmentSubStatus.OUT_FOR_DELIVERY);
        fulfillmentOrders.save(order);
        recordHistory(order, order.getStatus(), order.getStatus(), FulfillmentSubStatus.OUT_FOR_DELIVERY, actorId, "Dispatched");
        notifications.publish(new OutForDeliveryEvent(
                this, order.getOrganizationId(), actorId, order.getId()));
        return order;
    }

    public FulfillmentOrder markDelivered(UUID fulfillmentOrderId, UUID actorId) {
        FulfillmentOrder order = requireFulfillment(fulfillmentOrderId);
        DeliveryAssignment assignment = deliveryAssignments
                .findByFulfillmentOrderId(fulfillmentOrderId)
                .orElseThrow(() -> new BusinessException("Delivery assignment not found"));
        assignment.setStatus(DeliveryAssignmentStatus.DELIVERED);
        assignment.setDeliveredAt(OffsetDateTime.now());
        deliveryAssignments.save(assignment);

        order.setSubStatus(FulfillmentSubStatus.DELIVERED);
        FulfillmentContext ctx = buildContext(order, actorId);
        completeFulfillment(order, actorId, "Delivered");
        strategyRegistry.require(order.getFulfillmentType()).onComplete(ctx);
        notifications.publish(new DeliveredEvent(
                this, order.getOrganizationId(), actorId, order.getId()));
        return order;
    }

    public FulfillmentOrder completeScanAndGo(UUID fulfillmentOrderId, UUID actorId) {
        FulfillmentOrder order = requireFulfillment(fulfillmentOrderId);
        FulfillmentContext ctx = buildContext(order, actorId);
        if (order.getStatus() == FulfillmentOrderStatus.ACCEPTED) {
            transition(order, FulfillmentOrderStatus.FULFILLING, FulfillmentSubStatus.NONE, actorId, "Scan exit fulfilling");
        }
        erpDocuments.postAtMilestone(
                order.getFulfillmentType(),
                ctx.checkoutSession(),
                ctx.commerceOrder(),
                ctx.commerceOrder().getErpCustomerId(),
                ErpDocumentStrategy.ErpMilestone.SCAN_EXIT_VERIFIED);
        completeFulfillment(order, actorId, "Scan & Go exit verified");
        strategyRegistry.require(order.getFulfillmentType()).onComplete(ctx);
        return order;
    }

    public FulfillmentOrder cancel(UUID fulfillmentOrderId, UUID actorId, String reason) {
        FulfillmentOrder order = requireFulfillment(fulfillmentOrderId);
        if (order.getStatus() == FulfillmentOrderStatus.COMPLETED
                || order.getStatus() == FulfillmentOrderStatus.CANCELLED) {
            throw new BusinessException("Cannot cancel fulfillment order in " + order.getStatus());
        }
        transition(order, FulfillmentOrderStatus.CANCELLED, order.getSubStatus(), actorId, reason);
        order.setCancelledAt(OffsetDateTime.now());
        fulfillmentOrders.save(order);
        strategyRegistry.require(order.getFulfillmentType()).onCancel(buildContext(order, actorId));
        syncCommerceOrder(order, CommerceOrderStatus.CANCELLED);
        releaseReservations(order);
        return order;
    }

    private void completeFulfillment(FulfillmentOrder order, UUID actorId, String note) {
        transition(order, FulfillmentOrderStatus.COMPLETED, order.getSubStatus(), actorId, note);
        order.setCompletedAt(OffsetDateTime.now());
        fulfillmentOrders.save(order);
        syncCommerceOrder(order, CommerceOrderStatus.COMPLETED);
        releaseReservations(order);
        CommerceOrder commerceOrder = requireCommerceOrder(order.getCommerceOrderId());
        platformEvents.publishOrderCompleted(
                order.getOrganizationId(), commerceOrder.getId(), commerceOrder.getCustomerId());
    }

    private void releaseReservations(FulfillmentOrder order) {
        reservations.releaseForOrder(order.getOrganizationId(), order.getCommerceOrderId());
    }

    private void syncCommerceOrder(FulfillmentOrder fulfillment, CommerceOrderStatus status) {
        CommerceOrder commerceOrder = requireCommerceOrder(fulfillment.getCommerceOrderId());
        commerceOrder.setStatus(status);
        if (status == CommerceOrderStatus.IN_FULFILLMENT && commerceOrder.getConfirmedAt() == null) {
            commerceOrder.setConfirmedAt(OffsetDateTime.now());
        }
        commerceOrders.save(commerceOrder);
    }

    private FulfillmentContext buildContext(FulfillmentOrder order, UUID actorId) {
        CommerceOrder commerceOrder = requireCommerceOrder(order.getCommerceOrderId());
        CommerceCheckoutSession session = checkoutSessions
                .findById(commerceOrder.getCheckoutSessionId())
                .orElseThrow(() -> new ResourceNotFoundException("Checkout session not found"));
        List<CommerceOrderLine> lines = orderLines.findByOrderIdOrderByCreatedAtAsc(commerceOrder.getId());
        return new FulfillmentContext(order, commerceOrder, session, lines, actorId);
    }

    private FulfillmentOrder requireFulfillment(UUID id) {
        return fulfillmentOrders.findById(id).orElseThrow(() -> new ResourceNotFoundException("Fulfillment order not found"));
    }

    private void requirePickupFulfillment(FulfillmentOrder order) {
        if (order.getFulfillmentType() == FulfillmentType.HOME_DELIVERY) {
            throw new BusinessException("Home delivery orders are completed from the Delivery queue");
        }
        if (order.getFulfillmentType() != FulfillmentType.STORE_PICKUP
                && order.getFulfillmentType() != FulfillmentType.CLICK_AND_COLLECT) {
            throw new BusinessException("Order is not eligible for store pickup verification");
        }
    }

    private PickupSession ensurePickupSession(FulfillmentOrder order) {
        return pickupSessions.findByFulfillmentOrderId(order.getId()).orElseGet(() -> {
            PickupSession session = new PickupSession();
            session.setFulfillmentOrderId(order.getId());
            session.setCustomerId(order.getCustomerId());
            session.setStoreId(order.getStoreId());
            session.setStatus(PickupSessionStatus.WAITING);
            return pickupSessions.save(session);
        });
    }

    private CommerceOrder requireCommerceOrder(UUID id) {
        return commerceOrders.findById(id).orElseThrow(() -> new ResourceNotFoundException("Commerce order not found"));
    }

    private void assertStatus(FulfillmentOrder order, FulfillmentOrderStatus expected) {
        if (order.getStatus() != expected) {
            throw new BusinessException("Expected fulfillment status " + expected + " but was " + order.getStatus());
        }
    }

    private void transition(
            FulfillmentOrder order,
            FulfillmentOrderStatus to,
            FulfillmentSubStatus subStatus,
            UUID actorId,
            String note) {
        FulfillmentOrderStatus from = order.getStatus();
        transitionRules.assertTransition(from, to);
        order.setStatus(to);
        if (subStatus != null) {
            order.setSubStatus(subStatus);
        }
        fulfillmentOrders.save(order);
        recordHistory(order, from, to, order.getSubStatus(), actorId, note);
    }

    private void recordHistory(
            FulfillmentOrder order,
            FulfillmentOrderStatus from,
            FulfillmentOrderStatus to,
            FulfillmentSubStatus subStatus,
            UUID actorId,
            String note) {
        FulfillmentStatusHistory history = new FulfillmentStatusHistory();
        history.setFulfillmentOrderId(order.getId());
        history.setFromStatus(from != null ? from.name() : null);
        history.setToStatus(to.name());
        history.setSubStatus(subStatus != null ? subStatus.name() : null);
        history.setActorId(actorId);
        history.setNote(note);
        statusHistory.save(history);
    }
}
