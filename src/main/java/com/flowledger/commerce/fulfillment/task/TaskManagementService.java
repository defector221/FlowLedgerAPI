package com.flowledger.commerce.fulfillment.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.commerce.fulfillment.order.domain.FulfillmentOrderStatus;
import com.flowledger.commerce.fulfillment.order.entity.FulfillmentOrder;
import com.flowledger.commerce.fulfillment.order.repository.FulfillmentOrderRepository;
import com.flowledger.commerce.fulfillment.task.domain.TaskStatus;
import com.flowledger.commerce.fulfillment.task.entity.PackingTask;
import com.flowledger.commerce.fulfillment.task.entity.PickingTask;
import com.flowledger.commerce.fulfillment.task.repository.PackingTaskRepository;
import com.flowledger.commerce.fulfillment.task.repository.PickingTaskRepository;
import com.flowledger.commerce.order.entity.CommerceOrderLine;
import com.flowledger.common.exception.BusinessException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TaskManagementService {
    private final PickingTaskRepository pickingTasks;
    private final PackingTaskRepository packingTasks;
    private final FulfillmentOrderRepository fulfillmentOrders;
    private final ObjectMapper objectMapper;

    public TaskManagementService(
            PickingTaskRepository pickingTasks,
            PackingTaskRepository packingTasks,
            FulfillmentOrderRepository fulfillmentOrders,
            ObjectMapper objectMapper) {
        this.pickingTasks = pickingTasks;
        this.packingTasks = packingTasks;
        this.fulfillmentOrders = fulfillmentOrders;
        this.objectMapper = objectMapper;
    }

    public PickingTask createPickingTask(FulfillmentOrder order, List<CommerceOrderLine> lines) {
        PickingTask task = new PickingTask();
        task.setFulfillmentOrderId(order.getId());
        task.setStoreId(order.getStoreId());
        task.setStatus(TaskStatus.PENDING);
        task.setLineItems(toJson(lines));
        return pickingTasks.save(task);
    }

    public PickingTask startPicking(UUID fulfillmentOrderId, UUID pickerId) {
        PickingTask task = requirePickingTask(fulfillmentOrderId);
        if (task.getStatus() != TaskStatus.PENDING) {
            throw new BusinessException("Picking task is not pending");
        }
        task.setPickerId(pickerId);
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setStartedAt(OffsetDateTime.now());
        FulfillmentOrder order = requireOrder(fulfillmentOrderId);
        order.setAssignedPickerId(pickerId);
        fulfillmentOrders.save(order);
        return pickingTasks.save(task);
    }

    public PickingTask completePicking(UUID fulfillmentOrderId, UUID actorId) {
        PickingTask task = requirePickingTask(fulfillmentOrderId);
        if (task.getStatus() == TaskStatus.PENDING) {
            task.setPickerId(actorId);
            task.setStatus(TaskStatus.IN_PROGRESS);
            task.setStartedAt(OffsetDateTime.now());
            FulfillmentOrder order = requireOrder(fulfillmentOrderId);
            order.setAssignedPickerId(actorId);
            fulfillmentOrders.save(order);
            pickingTasks.save(task);
        } else if (task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw new BusinessException("Picking task is not in progress");
        }
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(OffsetDateTime.now());
        pickingTasks.save(task);

        PackingTask packing = new PackingTask();
        packing.setFulfillmentOrderId(fulfillmentOrderId);
        packing.setPickingTaskId(task.getId());
        packing.setStoreId(task.getStoreId());
        packing.setStatus(TaskStatus.PENDING);
        packing.setLineItems(task.getLineItems());
        packingTasks.save(packing);
        return task;
    }

    public PackingTask startPacking(UUID fulfillmentOrderId, UUID packerId) {
        PackingTask task = requirePackingTask(fulfillmentOrderId);
        if (task.getStatus() != TaskStatus.PENDING) {
            throw new BusinessException("Packing task is not pending");
        }
        task.setPackerId(packerId);
        task.setStatus(TaskStatus.IN_PROGRESS);
        task.setStartedAt(OffsetDateTime.now());
        return packingTasks.save(task);
    }

    public PackingTask completePacking(UUID fulfillmentOrderId, UUID actorId) {
        PackingTask task = requirePackingTask(fulfillmentOrderId);
        if (task.getStatus() == TaskStatus.PENDING) {
            task.setPackerId(actorId);
            task.setStatus(TaskStatus.IN_PROGRESS);
            task.setStartedAt(OffsetDateTime.now());
            packingTasks.save(task);
        } else if (task.getStatus() != TaskStatus.IN_PROGRESS) {
            throw new BusinessException("Packing task is not in progress");
        }
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(OffsetDateTime.now());
        return packingTasks.save(task);
    }

    public List<PickingTask> listOpenPicking(UUID storeId) {
        return pickingTasks.findByStoreIdAndStatusIn(
                storeId, List.of(TaskStatus.PENDING, TaskStatus.IN_PROGRESS));
    }

    public List<PackingTask> listOpenPacking(UUID storeId) {
        return packingTasks.findByStoreIdAndStatusIn(
                storeId, List.of(TaskStatus.PENDING, TaskStatus.IN_PROGRESS));
    }

    private PickingTask requirePickingTask(UUID fulfillmentOrderId) {
        return pickingTasks
                .findByFulfillmentOrderId(fulfillmentOrderId)
                .orElseThrow(() -> new BusinessException("Picking task not found"));
    }

    private PackingTask requirePackingTask(UUID fulfillmentOrderId) {
        return packingTasks
                .findByFulfillmentOrderId(fulfillmentOrderId)
                .orElseThrow(() -> new BusinessException("Packing task not found"));
    }

    private FulfillmentOrder requireOrder(UUID fulfillmentOrderId) {
        return fulfillmentOrders
                .findById(fulfillmentOrderId)
                .orElseThrow(() -> new BusinessException("Fulfillment order not found"));
    }

    private String toJson(List<CommerceOrderLine> lines) {
        try {
            List<Map<String, Object>> payload = lines.stream()
                    .map(line -> Map.<String, Object>of(
                            "lineId", line.getId(),
                            "productId", line.getProductId(),
                            "quantity", line.getQuantity()))
                    .toList();
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
