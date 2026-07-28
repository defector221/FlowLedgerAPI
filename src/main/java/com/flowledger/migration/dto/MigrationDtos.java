package com.flowledger.migration.dto;

import com.flowledger.migration.domain.ExportFormat;
import com.flowledger.migration.domain.ExportJobStatus;
import com.flowledger.migration.domain.ImportModule;
import com.flowledger.migration.domain.ImportRowStatus;
import com.flowledger.migration.domain.ValidationSeverity;
import com.flowledger.migration.mapping.FieldMapping;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MigrationDtos {
    private MigrationDtos() {}

    public record ModuleInfo(String code, String displayName, List<String> dependsOn, List<String> fields) {}

    public record SourceInfo(String code, String label, List<String> extensions) {}

    public record MappingUpdateRequest(List<FieldMapping> mappings, UUID mappingProfileId, Boolean autoCreateMissing) {}

    public record RowFixRequest(Map<String, String> normalized) {}

    public record MappingProfileRequest(
            @NotBlank String name,
            @NotNull ImportModule module,
            String sourceLabel,
            Boolean autoCreateMissing,
            List<FieldMapping> mappings) {}

    public record MappingProfileResponse(
            UUID id,
            String name,
            String module,
            String sourceLabel,
            boolean autoCreateMissing,
            List<FieldMapping> mappings,
            boolean active,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record ExportRequest(@NotNull ImportModule module, @NotNull ExportFormat format) {}

    public record ImportJobResponse(
            UUID id,
            String module,
            String sourceType,
            String status,
            String fileName,
            UUID mappingProfileId,
            int totalRows,
            int processedRows,
            int successRows,
            int errorRows,
            int skippedRows,
            BigDecimal progressPct,
            List<String> columns,
            List<FieldMapping> mapping,
            String errorSummary,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record PreviewRow(
            int rowNumber,
            ImportRowStatus status,
            Map<String, String> raw,
            Map<String, String> normalized,
            String message,
            List<ValidationIssue> errors,
            String entityType,
            UUID entityId) {}

    public record ValidationIssue(String field, String code, String message, ValidationSeverity severity) {}

    public record ImportReportResponse(
            UUID id,
            UUID jobId,
            String status,
            int totalRows,
            int successRows,
            int errorRows,
            int skippedRows,
            Map<String, Object> summary,
            String errorFileObjectKey,
            String errorFileDownloadUrl,
            OffsetDateTime createdAt) {}

    public record ExportJobResponse(
            UUID id,
            String module,
            String format,
            ExportJobStatus status,
            String fileName,
            int totalRows,
            String errorMessage,
            String downloadUrl,
            OffsetDateTime startedAt,
            OffsetDateTime createdAt,
            OffsetDateTime completedAt) {}

    public static ImportJobResponse toJob(
            com.flowledger.migration.entity.ImportJob job, List<String> columns, List<FieldMapping> mappings) {
        return new ImportJobResponse(
                job.getId(),
                job.getModule().name(),
                job.getSourceType().name(),
                job.getStatus().name(),
                job.getFileName(),
                job.getMappingProfileId(),
                job.getTotalRows(),
                job.getProcessedRows(),
                job.getSuccessRows(),
                job.getErrorRows(),
                job.getSkippedRows(),
                job.getProgressPct(),
                columns,
                mappings,
                job.getErrorSummary(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getCreatedAt(),
                job.getUpdatedAt());
    }
}
