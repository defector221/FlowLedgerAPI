package com.flowledger.migration.job;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.migration.audit.MigrationAuditService;
import com.flowledger.migration.domain.ImportJobStatus;
import com.flowledger.migration.domain.ImportRowStatus;
import com.flowledger.migration.entity.ImportJob;
import com.flowledger.migration.entity.ImportReport;
import com.flowledger.migration.entity.ImportRowResult;
import com.flowledger.migration.repository.ImportJobRepository;
import com.flowledger.migration.repository.ImportReportRepository;
import com.flowledger.migration.repository.ImportRowResultRepository;
import com.flowledger.migration.writer.DocumentModuleWriter;
import com.flowledger.migration.writer.ModuleWriter;
import com.flowledger.migration.writer.ModuleWriter.WriteResult;
import com.flowledger.migration.writer.ModuleWriterRegistry;
import com.flowledger.storage.BytesMultipartFile;
import com.flowledger.storage.StorageService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ImportWorker {
    private static final Logger log = LoggerFactory.getLogger(ImportWorker.class);
    private static final int CHUNK = 100;

    private final ImportJobRepository jobs;
    private final ImportRowResultRepository rows;
    private final ImportReportRepository reports;
    private final ModuleWriterRegistry writers;
    private final ObjectMapper objectMapper;
    private final StorageService storage;
    private final MigrationAuditService audit;

    public ImportWorker(
            ImportJobRepository jobs,
            ImportRowResultRepository rows,
            ImportReportRepository reports,
            ModuleWriterRegistry writers,
            ObjectMapper objectMapper,
            StorageService storage,
            MigrationAuditService audit) {
        this.jobs = jobs;
        this.rows = rows;
        this.reports = reports;
        this.writers = writers;
        this.objectMapper = objectMapper;
        this.storage = storage;
        this.audit = audit;
    }

    @Async
    @Transactional
    public void processAsync(UUID jobId, UUID organizationId, UUID userId) {
        try {
            TenantContext.set(organizationId, userId);
            process(jobId, organizationId);
        } catch (Exception e) {
            log.error("Import job {} failed", jobId, e);
            jobs.findByIdAndOrganizationId(jobId, organizationId).ifPresent(job -> {
                job.setStatus(ImportJobStatus.FAILED);
                job.setErrorSummary(e.getMessage());
                job.setCompletedAt(OffsetDateTime.now());
                jobs.save(job);
            });
        } finally {
            TenantContext.clear();
        }
    }

    private void process(UUID jobId, UUID organizationId) {
        ImportJob job = jobs.findByIdAndOrganizationId(jobId, organizationId).orElse(null);
        if (job == null) return;
        job.setStatus(ImportJobStatus.RUNNING);
        job.setStartedAt(OffsetDateTime.now());
        jobs.save(job);

        ModuleWriter writer = writers.require(job.getModule());
        List<ImportRowResult> candidates = rows.findByJobIdAndOrganizationIdAndStatusInOrderByRowNumberAsc(
                jobId,
                organizationId,
                List.of(ImportRowStatus.OK, ImportRowStatus.WARNING));

        int success = 0;
        int skipped = 0;
        int failed = 0;
        List<String> errorLines = new ArrayList<>();
        errorLines.add("row_number,message");

        if (writer instanceof DocumentModuleWriter docWriter) {
            Map<String, List<ImportRowResult>> groups = new LinkedHashMap<>();
            for (ImportRowResult r : candidates) {
                Map<String, String> norm = readMap(r.getNormalizedJson());
                String key = docWriter.groupKey(norm);
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
            }
            int processed = 0;
            int totalGroups = groups.size();
            for (var entry : groups.entrySet()) {
                List<ImportRowResult> group = entry.getValue();
                List<Map<String, String>> norms =
                        group.stream().map(r -> readMap(r.getNormalizedJson())).toList();
                try {
                    WriteResult result = docWriter.writeDocument(organizationId, norms);
                    for (ImportRowResult r : group) {
                        applyResult(r, result);
                        if (result.skipped()) skipped++;
                        else if (result.success()) success++;
                        else {
                            failed++;
                            errorLines.add(r.getRowNumber() + ",\"" + escape(result.message()) + "\"");
                        }
                    }
                } catch (Exception e) {
                    for (ImportRowResult r : group) {
                        r.setStatus(ImportRowStatus.ERROR);
                        r.setMessage(e.getMessage());
                        failed++;
                        errorLines.add(r.getRowNumber() + ",\"" + escape(e.getMessage()) + "\"");
                    }
                }
                rows.saveAll(group);
                processed++;
                updateProgress(job, processed, totalGroups, success, skipped, failed);
            }
        } else {
            for (int i = 0; i < candidates.size(); i += CHUNK) {
                List<ImportRowResult> chunk =
                        candidates.subList(i, Math.min(i + CHUNK, candidates.size()));
                for (ImportRowResult r : chunk) {
                    if (r.getStatus() == ImportRowStatus.IMPORTED) {
                        skipped++;
                        continue;
                    }
                    try {
                        WriteResult result = writer.write(organizationId, readMap(r.getNormalizedJson()));
                        applyResult(r, result);
                        if (result.skipped()) skipped++;
                        else if (result.success()) success++;
                        else {
                            failed++;
                            errorLines.add(r.getRowNumber() + ",\"" + escape(result.message()) + "\"");
                        }
                    } catch (Exception e) {
                        r.setStatus(ImportRowStatus.ERROR);
                        r.setMessage(e.getMessage());
                        failed++;
                        errorLines.add(r.getRowNumber() + ",\"" + escape(e.getMessage()) + "\"");
                    }
                }
                rows.saveAll(chunk);
                updateProgress(job, Math.min(i + CHUNK, candidates.size()), candidates.size(), success, skipped, failed);
            }
        }

        String errorKey = null;
        if (errorLines.size() > 1) {
            errorKey = "migration/" + organizationId + "/errors/" + jobId + ".csv";
            byte[] csv = String.join("\n", errorLines).getBytes(StandardCharsets.UTF_8);
            storage.store(errorKey, new BytesMultipartFile("errors.csv", "text/csv", csv));
        }

        ImportReport report = reports
                .findByJobIdAndOrganizationId(jobId, organizationId)
                .orElseGet(ImportReport::new);
        report.setOrganizationId(organizationId);
        report.setJobId(jobId);
        report.setErrorFileObjectKey(errorKey);
        try {
            report.setSummaryJson(objectMapper.writeValueAsString(Map.of(
                    "successRows", success,
                    "skippedRows", skipped,
                    "errorRows", failed,
                    "module", job.getModule().name())));
        } catch (Exception e) {
            report.setSummaryJson("{}");
        }
        reports.save(report);

        job.setSuccessRows(success);
        job.setSkippedRows(skipped);
        job.setErrorRows(failed);
        job.setProcessedRows(success + skipped + failed);
        job.setProgressPct(BigDecimal.valueOf(100));
        job.setCompletedAt(OffsetDateTime.now());
        job.setStatus(failed > 0 ? ImportJobStatus.COMPLETED_WITH_ERRORS : ImportJobStatus.COMPLETED);
        jobs.save(job);
        audit.log(
                jobId,
                "COMPLETED",
                job.getModule().name(),
                Map.of("success", success, "skipped", skipped, "failed", failed));
    }

    private void applyResult(ImportRowResult r, WriteResult result) {
        if (result.skipped()) {
            r.setStatus(ImportRowStatus.SKIPPED);
            r.setMessage(result.message());
        } else if (result.success()) {
            r.setStatus(ImportRowStatus.IMPORTED);
            r.setEntityId(result.entityId());
            r.setEntityType(result.entityType());
            r.setMessage(null);
        } else {
            r.setStatus(ImportRowStatus.ERROR);
            r.setMessage(result.message());
        }
    }

    private void updateProgress(
            ImportJob job, int processed, int total, int success, int skipped, int failed) {
        job.setProcessedRows(processed);
        job.setSuccessRows(success);
        job.setSkippedRows(skipped);
        job.setErrorRows(failed);
        if (total > 0) {
            job.setProgressPct(BigDecimal.valueOf(processed * 100.0 / total).setScale(2, RoundingMode.HALF_UP));
        }
        jobs.save(job);
    }

    private Map<String, String> readMap(String json) {
        try {
            return objectMapper.readValue(json == null ? "{}" : json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\"", "\"\"");
    }
}
