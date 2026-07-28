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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "commerce_picking_tasks")
@Getter
@Setter
@NoArgsConstructor
public class PickingTask extends CommerceGlobalEntity {
    @Column(name = "fulfillment_order_id", nullable = false)
    private UUID fulfillmentOrderId;

    @Column(name = "store_id", nullable = false)
    private UUID storeId;

    @Column(name = "picker_id")
    private UUID pickerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskStatus status = TaskStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "line_items", nullable = false, columnDefinition = "jsonb")
    private String lineItems = "[]";

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;
}
