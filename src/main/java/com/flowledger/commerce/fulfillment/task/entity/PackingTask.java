package com.flowledger.commerce.fulfillment.task.entity;

import com.flowledger.commerce.common.CommerceGlobalEntity;
import com.flowledger.commerce.fulfillment.task.domain.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "commerce_packing_tasks")
@Getter
@Setter
@NoArgsConstructor
public class PackingTask extends CommerceGlobalEntity {
    @Column(name = "fulfillment_order_id", nullable = false)
    private UUID fulfillmentOrderId;

    @Column(name = "picking_task_id")
    private UUID pickingTaskId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "packer_id")
    private UUID packerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.PENDING;

    @Column(name = "line_items", nullable = false, columnDefinition = "jsonb")
    private String lineItems = "[]";

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
}
