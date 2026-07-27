package com.flowledger.migration.job;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowledger.common.dto.PageResponse;
import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.migration.audit.MigrationAuditService;
import com.flowledger.migration.domain.ImportJobStatus;
import com.flowledger.migration.domain.ImportModule;
import com.flowledger.migration.domain.ImportRowStatus;
import com.flowledger.migration.domain.ImportSourceType;
import com.flowledger.migration.dto.MigrationDtos;
import com.flowledger.migration.dto.MigrationDtos.*;
import com.flowledger.migration.entity.ImportJob;
import com.flowledger.migration.entity.ImportReport;
import com.flowledger.migration.entity.ImportRowResult;
import com.flowledger.migration.entity.ImportValidationError;
import com.flowledger.migration.mapping.FieldMapping;
import com.flowledger.migration.mapping.MappingEngine;
import com.flowledger.migration.mapping.ModuleFieldCatalog;
import com.flowledger.migration.parser.ParsedSheet;
import com.flowledger.migration.parser.ParserEngine;
import com.flowledger.migration.repository.ImportJobRepository;
import com.flowledger.migration.repository.ImportReportRepository;
import com.flowledger.migration.repository.ImportRowResultRepository;
import com.flowledger.migration.repository.ImportValidationErrorRepository;
import com.flowledger.migration.validation.ValidationEngine;
import com.flowledger.storage.StorageService;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImportJobService {
    private final ImportJobRepository jobs;
    private final ImportRowResultRepository rows;
    private final ImportValidationErrorRepository errors;
    private final ImportReportRepository reports;
    private final ParserEngine parser;
    private final MappingEngine mappingEngine;
    private final ModuleFieldCatalog catalog;
    private final ValidationEngine validation;
    private final StorageService storage;
    private final ObjectMapper objectMapper;
    private final MigrationAuditService audit;
    private final ImportWorker worker;

    public ImportJobService(
            ImportJobRepository jobs,
            ImportRowResultRepository rows,
            ImportValidationErrorRepository errors,
            ImportReportRepository reports,
            ParserEngine parser,
            MappingEngine mappingEngine,
            ModuleFieldCatalog catalog,
            ValidationEngine validation,
            StorageService storage,
            ObjectMapper objectMapper,
            MigrationAuditService audit,
            ImportWorker worker) {
        this.jobs = jobs;
        this.rows = rows;
        this.errors = errors;
        this.reports = reports;
        this.parser = parser;
        this.mappingEngine = mappingEngine;
        this.catalog = catalog;
        this.validation = validation;
        this.storage = storage;
        this.objectMapper = objectMapper;
        this.audit = audit;
        this.worker = worker;
    }

    @Transactional(readOnly = true)
    public List<ModuleInfo> modules() {
        List<ModuleInfo> list = new ArrayList<>();
        for (ImportModule m : ImportModule.values()) {
            list.add(new ModuleInfo(m.name(), m.displayName(), m.dependsOn(), catalog.fieldsFor(m)));
        }
        return list;
    }

    @Transactional(readOnly = true)
    public List<SourceInfo> sources() {
        return List.of(
                new SourceInfo("CSV", "CSV file", List.of(".csv")),
                new SourceInfo("XLSX", "Excel (.xlsx)", List.of(".xlsx", ".xls")),
                new SourceInfo("GENERIC", "Generic spreadsheet", List.of(".csv", ".xlsx")));
    }

    @Transactional
    public ImportJobResponse upload(ImportModule module, MultipartFile file) {
        UUID org = TenantContext.getOrganizationId();
        String fileName = file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename();
        ImportSourceType source = parser.detectSource(fileName);
        String key = "migration/" + org + "/" + UUID.randomUUID() + "/" + fileName;
        storage.store(key, file);

        ImportJob job = new ImportJob();
        job.setOrganizationId(org);
        job.setModule(module);
        job.setSourceType(source);
        job.setStatus(ImportJobStatus.UPLOADED);
        job.setFileObjectKey(key);
        job.setFileName(fileName);
        TenantContext.userId().ifPresent(job::setCreatedBy);
        ImportJob saved = jobs.save(job);
        audit.log(saved.getId(), "UPLOAD", module.name(), Map.of("fileName", fileName));
        return toResponse(saved);
    }

    @Transactional
    public ImportJobResponse detect(UUID jobId) {
        ImportJob job = requireJob(jobId);
        byte[] bytes = readBytes(job.getFileObjectKey());
        ParsedSheet sheet = parser.parsePrimary(bytes, job.getFileName());
        List<FieldMapping> auto = mappingEngine.autoMap(job.getModule(), sheet.columns());
        job.setColumnsJson(writeJson(sheet.columns()));
        job.setMappingJson(mappingEngine.toJson(auto));
        job.setTotalRows(sheet.rows().size());
        job.setStatus(ImportJobStatus.DETECTED);
        jobs.save(job);

        rows.deleteByJobId(job.getId());
        errors.deleteByJobId(job.getId());
        int rowNo = 1;
        List<ImportRowResult> batch = new ArrayList<>();
        for (Map<String, String> raw : sheet.rows()) {
            Map<String, String> normalized = mappingEngine.applyMapping(raw, auto);
            ImportRowResult r = new ImportRowResult();
            r.setOrganizationId(job.getOrganizationId());
            r.setJobId(job.getId());
            r.setRowNumber(rowNo++);
            r.setStatus(ImportRowStatus.PENDING);
            r.setRawJson(writeJson(raw));
            r.setNormalizedJson(writeJson(normalized));
            batch.add(r);
        }
        rows.saveAll(batch);
        audit.log(job.getId(), "DETECT", job.getModule().name(), Map.of("rows", sheet.rows().size()));
        return toResponse(job);
    }

    @Transactional
    public ImportJobResponse saveMapping(UUID jobId, MappingUpdateRequest request) {
        ImportJob job = requireJob(jobId);
        List<FieldMapping> mappings =
                request.mappings() == null ? List.of() : request.mappings();
        job.setMappingJson(mappingEngine.toJson(mappings));
        if (request.mappingProfileId() != null) {
            job.setMappingProfileId(request.mappingProfileId());
        }
        job.setStatus(ImportJobStatus.MAPPED);

        List<ImportRowResult> existing =
                rows.findByJobIdAndOrganizationIdAndStatusInOrderByRowNumberAsc(
                        jobId,
                        job.getOrganizationId(),
                        List.of(
                                ImportRowStatus.PENDING,
                                ImportRowStatus.OK,
                                ImportRowStatus.WARNING,
                                ImportRowStatus.ERROR));
        for (ImportRowResult r : existing) {
            Map<String, String> raw = readMap(r.getRawJson());
            r.setNormalizedJson(writeJson(mappingEngine.applyMapping(raw, mappings)));
            r.setStatus(ImportRowStatus.PENDING);
        }
        rows.saveAll(existing);
        jobs.save(job);
        return toResponse(job);
    }

    @Transactional
    public ImportJobResponse validate(UUID jobId) {
        ImportJob job = requireJob(jobId);
        List<ImportRowResult> existing = rows.findByJobIdAndOrganizationIdAndStatusInOrderByRowNumberAsc(
                jobId,
                job.getOrganizationId(),
                List.of(
                        ImportRowStatus.PENDING,
                        ImportRowStatus.OK,
                        ImportRowStatus.WARNING,
                        ImportRowStatus.ERROR));
        List<Map<String, String>> normalized = existing.stream()
                .map(r -> readMap(r.getNormalizedJson()))
                .toList();
        var results = validation.validate(job.getModule(), normalized);
        errors.deleteByJobId(jobId);
        int errorCount = 0;
        List<ImportValidationError> errorEntities = new ArrayList<>();
        for (int i = 0; i < existing.size(); i++) {
            ImportRowResult row = existing.get(i);
            var vr = results.get(i);
            row.setStatus(vr.status());
            if (!vr.issues().isEmpty()) {
                row.setMessage(vr.issues().get(0).message());
            } else {
                row.setMessage(null);
            }
            if (vr.status() == ImportRowStatus.ERROR) errorCount++;
            for (var issue : vr.issues()) {
                ImportValidationError e = new ImportValidationError();
                e.setOrganizationId(job.getOrganizationId());
                e.setJobId(jobId);
                e.setRowNumber(row.getRowNumber());
                e.setField(issue.field());
                e.setCode(issue.code());
                e.setMessage(issue.message());
                e.setSeverity(issue.severity());
                errorEntities.add(e);
            }
        }
        rows.saveAll(existing);
        errors.saveAll(errorEntities);
        job.setErrorRows(errorCount);
        job.setStatus(ImportJobStatus.VALIDATED);
        jobs.save(job);
        audit.log(jobId, "VALIDATE", job.getModule().name(), Map.of("errors", errorCount));
        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public PageResponse<PreviewRow> preview(UUID jobId, Pageable pageable) {
        ImportJob job = requireJob(jobId);
        var page = rows.findByJobIdAndOrganizationIdOrderByRowNumberAsc(jobId, job.getOrganizationId(), pageable);
        Map<Integer, List<ValidationIssue>> byRow = new LinkedHashMap<>();
        for (ImportValidationError e :
                errors.findByJobIdAndOrganizationIdOrderByRowNumberAsc(jobId, job.getOrganizationId())) {
            byRow.computeIfAbsent(e.getRowNumber(), k -> new ArrayList<>())
                    .add(new ValidationIssue(e.getField(), e.getCode(), e.getMessage(), e.getSeverity()));
        }
        List<PreviewRow> content = page.getContent().stream()
                .map(r -> new PreviewRow(
                        r.getRowNumber(),
                        r.getStatus(),
                        readMap(r.getRawJson()),
                        readMap(r.getNormalizedJson()),
                        r.getMessage(),
                        byRow.getOrDefault(r.getRowNumber(), List.of()),
                        r.getEntityType(),
                        r.getEntityId()))
                .toList();
        return PageResponse.of(content, pageable, page.getTotalElements());
    }

    @Transactional
    public PreviewRow fixRow(UUID jobId, int rowNo, RowFixRequest request) {
        ImportJob job = requireJob(jobId);
        ImportRowResult row = rows.findByJobIdAndRowNumberAndOrganizationId(jobId, rowNo, job.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Row not found"));
        Map<String, String> normalized = request.normalized() == null ? Map.of() : request.normalized();
        // allow partial patch from UI: merge into existing normalized
        Map<String, String> merged = new LinkedHashMap<>(readMap(row.getNormalizedJson()));
        merged.putAll(normalized);
        row.setNormalizedJson(writeJson(merged));
        row.setStatus(ImportRowStatus.PENDING);
        row.setMessage(null);
        rows.save(row);
        // remove prior errors for this row only
        for (ImportValidationError old : errors.findByJobIdAndRowNumber(jobId, rowNo)) {
            errors.delete(old);
        }
        var vr = validation.validate(job.getModule(), List.of(merged)).get(0);
        row.setStatus(vr.status());
        if (!vr.issues().isEmpty()) row.setMessage(vr.issues().get(0).message());
        rows.save(row);
        for (var issue : vr.issues()) {
            ImportValidationError e = new ImportValidationError();
            e.setOrganizationId(job.getOrganizationId());
            e.setJobId(jobId);
            e.setRowNumber(rowNo);
            e.setField(issue.field());
            e.setCode(issue.code());
            e.setMessage(issue.message());
            e.setSeverity(issue.severity());
            errors.save(e);
        }
        return new PreviewRow(
                row.getRowNumber(),
                row.getStatus(),
                readMap(row.getRawJson()),
                merged,
                row.getMessage(),
                vr.issues().stream()
                        .map(i -> new ValidationIssue(i.field(), i.code(), i.message(), i.severity()))
                        .toList(),
                row.getEntityType(),
                row.getEntityId());
    }

    @Transactional
    public ImportJobResponse commit(UUID jobId) {
        ImportJob job = requireJob(jobId);
        if (job.getStatus() != ImportJobStatus.VALIDATED
                && job.getStatus() != ImportJobStatus.COMPLETED_WITH_ERRORS
                && job.getStatus() != ImportJobStatus.FAILED) {
            // allow commit after validate; also retry
        }
        job.setStatus(ImportJobStatus.QUEUED);
        job.setProcessedRows(0);
        job.setSuccessRows(0);
        job.setSkippedRows(0);
        job.setProgressPct(BigDecimal.ZERO);
        jobs.save(job);
        audit.log(jobId, "COMMIT", job.getModule().name(), Map.of());
        UUID orgId = job.getOrganizationId();
        UUID userId = TenantContext.userId().orElse(null);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    worker.processAsync(jobId, orgId, userId);
                }
            });
        } else {
            worker.processAsync(jobId, orgId, userId);
        }
        return toResponse(job);
    }

    @Transactional(readOnly = true)
    public ImportJobResponse get(UUID jobId) {
        return toResponse(requireJob(jobId));
    }

    @Transactional(readOnly = true)
    public PageResponse<ImportJobResponse> history(Pageable pageable) {
        UUID org = TenantContext.getOrganizationId();
        var page = jobs.findByOrganizationIdOrderByCreatedAtDesc(org, pageable);
        return PageResponse.of(
                page.getContent().stream().map(this::toResponse).toList(), pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ImportReportResponse report(UUID jobId) {
        ImportJob job = requireJob(jobId);
        ImportReport report = reports
                .findByJobIdAndOrganizationId(jobId, job.getOrganizationId())
                .orElse(null);
        Map<String, Object> summary = report == null ? Map.of() : readObjectMap(report.getSummaryJson());
        String url = null;
        if (report != null && report.getErrorFileObjectKey() != null) {
            url = "/api/v1/migration/import/" + jobId + "/errors.csv";
        }
        return new ImportReportResponse(
                report == null ? null : report.getId(),
                jobId,
                job.getStatus().name(),
                job.getTotalRows(),
                job.getSuccessRows(),
                job.getErrorRows(),
                job.getSkippedRows(),
                summary,
                report == null ? null : report.getErrorFileObjectKey(),
                url,
                report == null ? job.getCreatedAt() : report.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public byte[] errorCsv(UUID jobId) {
        ImportJob job = requireJob(jobId);
        ImportReport report = reports
                .findByJobIdAndOrganizationId(jobId, job.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Report not found"));
        if (report.getErrorFileObjectKey() == null) {
            throw new ResourceNotFoundException("Error file not found");
        }
        try (InputStream in = storage.get(report.getErrorFileObjectKey())) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read error file", e);
        }
    }

    private ImportJob requireJob(UUID jobId) {
        return jobs.findByIdAndOrganizationId(jobId, TenantContext.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Import job not found"));
    }

    private byte[] readBytes(String key) {
        try (InputStream in = storage.get(key)) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to read uploaded file", e);
        }
    }

    private ImportJobResponse toResponse(ImportJob job) {
        return MigrationDtos.toJob(job, readStringList(job.getColumnsJson()), mappingEngine.fromJson(job.getMappingJson()));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, String> readMap(String json) {
        try {
            return objectMapper.readValue(json == null ? "{}" : json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private Map<String, Object> readObjectMap(String json) {
        try {
            return objectMapper.readValue(json == null ? "{}" : json, new TypeReference<>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }

    private List<String> readStringList(String json) {
        try {
            return objectMapper.readValue(json == null ? "[]" : json, new TypeReference<>() {});
        } catch (Exception e) {
            return List.of();
        }
    }
}
