package com.flowledger.commerce.fulfillment.task.repository;

import com.flowledger.commerce.fulfillment.task.domain.TaskStatus;
import com.flowledger.commerce.fulfillment.task.entity.PickingTask;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PickingTaskRepository extends JpaRepository<PickingTask, UUID> {
    Optional<PickingTask> findByFulfillmentOrderId(UUID fulfillmentOrderId);

    List<PickingTask> findByStoreIdAndStatusIn(UUID storeId, List<TaskStatus> statuses);
}
