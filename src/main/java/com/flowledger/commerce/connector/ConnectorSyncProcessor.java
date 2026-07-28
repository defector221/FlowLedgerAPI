package com.flowledger.commerce.connector;

import com.flowledger.commerce.connector.entity.CommerceConnectorSyncDlq;
import com.flowledger.commerce.connector.entity.CommerceConnectorSyncJob;
import com.flowledger.commerce.connector.repository.CommerceConnectorSyncDlqRepository;
import com.flowledger.commerce.connector.repository.CommerceConnectorSyncJobRepository;
import com.flowledger.platform.event.bus.PlatformEventDispatcher;
import com.flowledger.platform.event.bus.PlatformEventEnvelope;
import com.flowledger.platform.event.bus.PlatformEventTypes;
import jakarta.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ConnectorSyncProcessor {
    private static final Logger log = LoggerFactory.getLogger(ConnectorSyncProcessor.class);

    private final CommerceConnectorSyncJobRepository jobs;
    private final CommerceConnectorSyncDlqRepository dlq;
    private final ConnectorSyncService syncService;
    private final PlatformEventDispatcher dispatcher;

    public ConnectorSyncProcessor(
            CommerceConnectorSyncJobRepository jobs,
            CommerceConnectorSyncDlqRepository dlq,
            ConnectorSyncService syncService,
            PlatformEventDispatcher dispatcher) {
        this.jobs = jobs;
        this.dlq = dlq;
        this.syncService = syncService;
        this.dispatcher = dispatcher;
    }

    @PostConstruct
    void register() {
        dispatcher.register(PlatformEventTypes.STORE_PUBLISHED, this::onStorePublished);
        dispatcher.register(PlatformEventTypes.PRODUCT_UPDATED, this::onProductUpdated);
    }

    @Transactional
    void onStorePublished(PlatformEventEnvelope event) {
        UUID storeId = uuid(event.payload().get("storeId"));
        if (storeId == null || event.organizationId() == null) return;
        syncService.enqueue(
                event.organizationId(),
                "FLOWLEDGER",
                "STORE_SYNC",
                Map.of("storeId", storeId, "trigger", "StorePublished"));
    }

    @Transactional
    void onProductUpdated(PlatformEventEnvelope event) {
        if (event.organizationId() == null) return;
        syncService.enqueue(
                event.organizationId(),
                "REST",
                "PRODUCT_SYNC",
                Map.of(
                        "entityType", event.payload().getOrDefault("entityType", ""),
                        "entityId", event.payload().getOrDefault("entityId", ""),
                        "trigger", "ProductUpdated"));
    }

    @Scheduled(fixedDelayString = "${commerce.connector.poll-ms:5000}")
    @Transactional
    public void processDueJobs() {
        List<CommerceConnectorSyncJob> due = jobs.findDueJobs(PageRequest.of(0, 20));
        for (CommerceConnectorSyncJob job : due) {
            job.setStatus("RUNNING");
            job.setStartedAt(OffsetDateTime.now());
            jobs.save(job);
            try {
                execute(job);
                job.setStatus("COMPLETED");
                job.setCompletedAt(OffsetDateTime.now());
            } catch (Exception ex) {
                job.setAttemptCount(job.getAttemptCount() + 1);
                job.setLastError(ex.getMessage());
                if (job.getAttemptCount() >= job.getMaxAttempts()) {
                    job.setStatus("FAILED");
                    moveToDlq(job, ex.getMessage());
                } else {
                    job.setStatus("PENDING");
                    job.setScheduledAt(OffsetDateTime.now().plusMinutes(job.getAttemptCount()));
                }
                log.warn("Connector sync job {} failed: {}", job.getId(), ex.getMessage());
            }
            jobs.save(job);
        }
    }

    private void execute(CommerceConnectorSyncJob job) {
        // Staged adapter SPI — native FlowLedger sync is a no-op stub; REST/SAP/Oracle plugins plug in here
        log.debug("Executing connector sync {} {} for org {}", job.getConnectorType(), job.getSyncType(), job.getOrganizationId());
    }

    private void moveToDlq(CommerceConnectorSyncJob job, String error) {
        CommerceConnectorSyncDlq row = new CommerceConnectorSyncDlq();
        row.setJobId(job.getId());
        row.setOrganizationId(job.getOrganizationId());
        row.setPayload(job.getPayload());
        row.setErrorMessage(error);
        dlq.save(row);
    }

    private static UUID uuid(Object v) {
        if (v == null || v.toString().isBlank()) return null;
        return UUID.fromString(v.toString());
    }
}
