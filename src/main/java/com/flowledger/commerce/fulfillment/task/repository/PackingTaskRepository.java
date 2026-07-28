package com.flowledger.commerce.fulfillment.task.repository;

import com.flowledger.commerce.fulfillment.task.domain.TaskStatus;
import com.flowledger.commerce.fulfillment.task.entity.PackingTask;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PackingTaskRepository extends JpaRepository<PackingTask, UUID> {
    Optional<PackingTask> findByFulfillmentOrderId(UUID fulfillmentOrderId);

    List<PackingTask> findByStoreIdAndStatusIn(UUID storeId, List<TaskStatus> statuses);
}
