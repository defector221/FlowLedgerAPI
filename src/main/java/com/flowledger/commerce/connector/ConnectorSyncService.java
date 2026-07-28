package com.flowledger.commerce.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.commerce.connector.entity.CommerceConnectorSyncDlq;
import com.flowledger.commerce.connector.entity.CommerceConnectorSyncJob;
import com.flowledger.commerce.connector.repository.CommerceConnectorSyncDlqRepository;
import com.flowledger.commerce.connector.repository.CommerceConnectorSyncJobRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ConnectorSyncService {
    private final CommerceConnectorSyncJobRepository jobs;
    private final ObjectMapper objectMapper;

    public ConnectorSyncService(CommerceConnectorSyncJobRepository jobs, ObjectMapper objectMapper) {
        this.jobs = jobs;
        this.objectMapper = objectMapper;
    }

    public CommerceConnectorSyncJob enqueue(
            UUID organizationId, String connectorType, String syncType, Map<String, Object> payload) {
        CommerceConnectorSyncJob job = new CommerceConnectorSyncJob();
        job.setOrganizationId(organizationId);
        job.setConnectorType(connectorType);
        job.setSyncType(syncType);
        job.setStatus("PENDING");
        try {
            job.setPayload(objectMapper.writeValueAsString(payload != null ? payload : Map.of()));
        } catch (Exception e) {
            job.setPayload("{}");
        }
        job.setScheduledAt(OffsetDateTime.now());
        return jobs.save(job);
    }

    @Transactional(readOnly = true)
    public List<CommerceConnectorSyncJob> listJobs(UUID organizationId) {
        return jobs.findByOrganizationIdOrderByCreatedAtDesc(organizationId);
    }
}
