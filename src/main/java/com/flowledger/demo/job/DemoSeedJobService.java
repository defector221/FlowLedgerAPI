package com.flowledger.demo.job;

import com.flowledger.demo.DemoDataOrchestrator;
import com.flowledger.demo.DemoSeedRequest;
import com.flowledger.demo.DemoSeedResult;
import com.flowledger.demo.scenario.DemoScenario;
import com.flowledger.demo.scenario.DemoScenarioRegistry;
import com.flowledger.ops.service.OpsOrganizationService;
import com.flowledger.organization.repository.OrganizationRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DemoSeedJobService {
    private static final Logger log = LoggerFactory.getLogger(DemoSeedJobService.class);
    private static final int PIPELINE_STAGES = 12;
    private static final int MAX_JOBS = 50;

    private final ConcurrentHashMap<UUID, DemoSeedJob> jobs = new ConcurrentHashMap<>();
    private final DemoDataOrchestrator orchestrator;
    private final DemoScenarioRegistry registry;
    private final DemoSeedAsyncRunner asyncRunner;
    private final OrganizationRepository organizations;
    private final OpsOrganizationService organizationOps;

    public DemoSeedJobService(
            DemoDataOrchestrator orchestrator,
            DemoScenarioRegistry registry,
            DemoSeedAsyncRunner asyncRunner,
            OrganizationRepository organizations,
            @Lazy OpsOrganizationService organizationOps) {
        this.orchestrator = orchestrator;
        this.registry = registry;
        this.asyncRunner = asyncRunner;
        this.organizations = organizations;
        this.organizationOps = organizationOps;
    }

    public Map<String, Object> enqueue(DemoSeedRequest request) {
        DemoScenario scenario = DemoScenario.fromCommand(request != null ? request.scenario() : null);
        String orgName = request != null && request.organizationName() != null && !request.organizationName().isBlank()
                ? request.organizationName()
                : registry.require(scenario).organizationName();
        DemoSeedJob job = new DemoSeedJob(scenario.command(), orgName, PIPELINE_STAGES);
        jobs.put(job.getId(), job);
        trimOldJobs();
        asyncRunner.run(job.getId(), request == null ? new DemoSeedRequest(null, null, null, null) : request);
        return job.toMap();
    }

    public void execute(UUID jobId, DemoSeedRequest request) {
        DemoSeedJob job = jobs.get(jobId);
        if (job == null) {
            return;
        }
        job.markRunning();
        String orgName = job.getOrganizationName();
        try {
            DemoSeedResult result = orchestrator.seed(request, job);
            job.markCompleted(result);
        } catch (Exception ex) {
            log.error("Demo seed job {} failed", jobId, ex);
            String message = rootMessage(ex);
            job.markFailed(message);
            // Best-effort: remove leftover org when a stage committed then a later stage failed.
            purgeLeftoverOrg(orgName);
        }
    }

    private void purgeLeftoverOrg(String orgName) {
        if (orgName == null || orgName.isBlank()) {
            return;
        }
        try {
            organizations.findByNameIgnoreCase(orgName).ifPresent(org -> {
                log.warn("Purging leftover demo org {} after failed seed", org.getId());
                organizationOps.purge(org.getId(), org.getName());
            });
        } catch (Exception cleanupEx) {
            log.warn("Could not purge leftover org '{}': {}", orgName, cleanupEx.getMessage());
        }
    }

    private static String rootMessage(Throwable ex) {
        Throwable cur = ex;
        String best = ex.getMessage();
        while (cur != null) {
            if (cur instanceof ResponseStatusException rse && rse.getReason() != null) {
                best = rse.getStatusCode().value() + " " + rse.getReason();
            } else if (cur instanceof DataIntegrityViolationException dive) {
                best = dive.getMostSpecificCause() != null
                        ? dive.getMostSpecificCause().getMessage()
                        : dive.getMessage();
            } else if (cur.getMessage() != null && !cur.getMessage().isBlank()) {
                best = cur.getMessage();
            }
            cur = cur.getCause();
        }
        return best != null ? best : ex.getClass().getSimpleName();
    }

    public Optional<Map<String, Object>> get(UUID id) {
        DemoSeedJob job = jobs.get(id);
        return job == null ? Optional.empty() : Optional.of(job.toMap());
    }

    public List<Map<String, Object>> listRecent() {
        return jobs.values().stream()
                .sorted(Comparator.comparing(DemoSeedJob::getCreatedAt).reversed())
                .map(DemoSeedJob::toMap)
                .toList();
    }

    private void trimOldJobs() {
        if (jobs.size() <= MAX_JOBS) {
            return;
        }
        jobs.values().stream()
                .sorted(Comparator.comparing(DemoSeedJob::getCreatedAt))
                .limit(Math.max(0, jobs.size() - MAX_JOBS))
                .map(DemoSeedJob::getId)
                .forEach(jobs::remove);
    }
}
