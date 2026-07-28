package com.flowledger.commerce.fulfillment.task;

import com.flowledger.commerce.fulfillment.task.domain.TaskStatus;
import com.flowledger.commerce.fulfillment.task.entity.PickingTask;
import com.flowledger.commerce.fulfillment.task.repository.PickingTaskRepository;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class FulfillmentWorkloadService {
    private final PickingTaskRepository pickingTasks;

    public FulfillmentWorkloadService(PickingTaskRepository pickingTasks) {
        this.pickingTasks = pickingTasks;
    }

    public UUID suggestPicker(UUID storeId, List<UUID> activePickers) {
        if (activePickers == null || activePickers.isEmpty()) {
            return null;
        }
        return activePickers.stream()
                .min(Comparator.comparingLong(picker -> openTaskCount(storeId, picker)))
                .orElse(null);
    }

    private long openTaskCount(UUID storeId, UUID pickerId) {
        return pickingTasks.findByStoreIdAndStatusIn(storeId, List.of(TaskStatus.PENDING, TaskStatus.IN_PROGRESS))
                .stream()
                .filter(t -> pickerId.equals(t.getPickerId()))
                .count();
    }
}
