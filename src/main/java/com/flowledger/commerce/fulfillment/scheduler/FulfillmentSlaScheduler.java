package com.flowledger.commerce.fulfillment.scheduler;

import com.flowledger.commerce.fulfillment.order.domain.FulfillmentOrderStatus;
import com.flowledger.commerce.fulfillment.order.repository.FulfillmentOrderRepository;
import com.flowledger.commerce.fulfillment.task.domain.TaskStatus;
import com.flowledger.commerce.fulfillment.task.repository.PickingTaskRepository;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FulfillmentSlaScheduler {
    private static final Logger log = LoggerFactory.getLogger(FulfillmentSlaScheduler.class);

    private final FulfillmentOrderRepository fulfillmentOrders;
    private final PickingTaskRepository pickingTasks;

    public FulfillmentSlaScheduler(
            FulfillmentOrderRepository fulfillmentOrders, PickingTaskRepository pickingTasks) {
        this.fulfillmentOrders = fulfillmentOrders;
        this.pickingTasks = pickingTasks;
    }

    @Scheduled(fixedDelayString = "${commerce.fulfillment.sla-check-ms:300000}")
    public void checkStaleTasks() {
        OffsetDateTime threshold = OffsetDateTime.now().minusHours(4);
        long stalePicking = pickingTasks.findAll().stream()
                .filter(t -> t.getStatus() == TaskStatus.IN_PROGRESS)
                .filter(t -> t.getStartedAt() != null && t.getStartedAt().isBefore(threshold))
                .count();
        long staleAccepted = fulfillmentOrders.findAll().stream()
                .filter(o -> o.getStatus() == FulfillmentOrderStatus.ACCEPTED)
                .filter(o -> o.getAcceptedAt() != null && o.getAcceptedAt().isBefore(threshold))
                .count();
        if (stalePicking > 0 || staleAccepted > 0) {
            log.info("Fulfillment SLA check: stalePicking={} staleAccepted={}", stalePicking, staleAccepted);
        }
    }
}
