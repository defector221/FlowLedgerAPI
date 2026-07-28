package com.flowledger.commerce.service;

import com.flowledger.commerce.config.CommerceModuleGuard;
import com.flowledger.commerce.dto.CommerceDtos;
import com.flowledger.commerce.marketplace.sync.MarketplaceSyncJob;
import com.flowledger.commerce.marketplace.sync.MarketplaceSyncJobRepository;
import com.flowledger.common.exception.ResourceNotFoundException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CommerceJobService {
    private final CommerceModuleGuard guard;
    private final MarketplaceSyncJobRepository jobs;

    public CommerceJobService(CommerceModuleGuard guard, MarketplaceSyncJobRepository jobs) {
        this.guard = guard;
        this.jobs = jobs;
    }

    public CommerceDtos.CommerceJobResponse getJob(UUID jobId) {
        UUID orgId = guard.ensureEnabled();
        MarketplaceSyncJob job = jobs.findByIdAndOrganizationId(jobId, orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Commerce job not found"));
        return toResponse(job);
    }

    public List<CommerceDtos.CommerceJobResponse> listRecentJobs() {
        UUID orgId = guard.ensureEnabled();
        return jobs.findTop10ByOrganizationIdOrderByCreatedAtDesc(orgId).stream()
                .map(this::toResponse)
                .toList();
    }

    private CommerceDtos.CommerceJobResponse toResponse(MarketplaceSyncJob job) {
        return new CommerceDtos.CommerceJobResponse(
                job.getId(),
                job.getJobType(),
                job.getEntityType(),
                job.getOrganizationId(),
                job.getStoreId(),
                job.getEntityId(),
                job.getStatus(),
                job.getSyncMode(),
                job.getLastError(),
                job.getResultDetail(),
                job.getAttempts(),
                job.getScheduledAt(),
                job.getCompletedAt(),
                job.getCreatedAt());
    }
}
