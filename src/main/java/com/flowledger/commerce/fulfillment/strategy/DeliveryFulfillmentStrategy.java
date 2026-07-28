package com.flowledger.commerce.fulfillment.strategy;

import com.flowledger.commerce.fulfillment.FulfillmentType;
import com.flowledger.commerce.fulfillment.delivery.entity.DeliveryAssignment;
import com.flowledger.commerce.fulfillment.delivery.domain.DeliveryAssignmentStatus;
import com.flowledger.commerce.fulfillment.delivery.repository.DeliveryAssignmentRepository;
import com.flowledger.commerce.fulfillment.engine.FulfillmentContext;
import com.flowledger.commerce.fulfillment.order.domain.FulfillmentSubStatus;
import com.flowledger.commerce.fulfillment.pickup.entity.PickupSession;
import com.flowledger.commerce.fulfillment.pickup.domain.PickupSessionStatus;
import com.flowledger.commerce.fulfillment.pickup.repository.PickupSessionRepository;
import com.flowledger.commerce.order.erp.ErpDocumentService;
import com.flowledger.commerce.order.erp.ErpDocumentStrategy;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DeliveryFulfillmentStrategy implements FulfillmentStrategy {
    private final DeliveryAssignmentRepository assignments;
    private final ErpDocumentService erpDocuments;

    public DeliveryFulfillmentStrategy(DeliveryAssignmentRepository assignments, ErpDocumentService erpDocuments) {
        this.assignments = assignments;
        this.erpDocuments = erpDocuments;
    }

    @Override
    public FulfillmentType type() {
        return FulfillmentType.HOME_DELIVERY;
    }

    @Override
    public boolean requiresPicking() {
        return true;
    }

    @Override
    public boolean requiresPacking() {
        return true;
    }

    @Override
    public void onAccepted(FulfillmentContext ctx) {
        erpDocuments.postAtMilestone(
                type(),
                ctx.checkoutSession(),
                ctx.commerceOrder(),
                ctx.commerceOrder().getErpCustomerId(),
                ErpDocumentStrategy.ErpMilestone.ACCEPTED);
        DeliveryAssignment assignment = new DeliveryAssignment();
        assignment.setFulfillmentOrderId(ctx.fulfillmentOrder().getId());
        assignment.setStoreId(ctx.fulfillmentOrder().getStoreId());
        assignment.setStatus(DeliveryAssignmentStatus.PENDING);
        assignments.save(assignment);
    }

    @Override
    public void onReady(FulfillmentContext ctx) {}

    @Override
    public void onFulfillmentStart(FulfillmentContext ctx) {}

    @Override
    public void onComplete(FulfillmentContext ctx) {}

    @Override
    public void onCancel(FulfillmentContext ctx) {}

    @Override
    public List<String> milestoneLabels() {
        return List.of("Accepted", "Picking", "Packing", "Ready", "Driver Assigned", "Out for Delivery", "Delivered");
    }
}
