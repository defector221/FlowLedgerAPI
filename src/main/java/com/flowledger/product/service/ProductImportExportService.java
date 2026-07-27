package com.flowledger.product.service;

import com.flowledger.common.service.OrganizationScopedService;
import com.flowledger.migration.domain.ExportFormat;
import com.flowledger.migration.domain.ImportModule;
import com.flowledger.migration.dto.MigrationDtos.ExportRequest;
import com.flowledger.migration.export.ExportEngine;
import com.flowledger.migration.job.ImportJobService;
import com.flowledger.product.dto.ProductIdentificationDtos.ExportBarcodesRequest;
import com.flowledger.product.dto.ProductIdentificationDtos.ImportCommitRequest;
import com.flowledger.product.dto.ProductIdentificationDtos.ImportJobResponse;
import com.flowledger.product.dto.ProductIdentificationDtos.ImportPreviewResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Thin alias over the Migration Center pipeline for legacy {@code /products/import/*} endpoints.
 * Prefer {@code /api/v1/migration/*} for new clients.
 */
@Service
@Transactional
public class ProductImportExportService extends OrganizationScopedService {
    private final ImportJobService imports;
    private final ExportEngine exports;

    public ProductImportExportService(ImportJobService imports, ExportEngine exports) {
        this.imports = imports;
        this.exports = exports;
    }

    public ImportPreviewResponse preview(MultipartFile file) {
        var job = imports.upload(ImportModule.BARCODE, file);
        job = imports.detect(job.id());
        return new ImportPreviewResponse(
                job.id(),
                job.status(),
                List.of(Map.of(
                        "message",
                        "Delegated to Migration Center (BARCODE). Open Data Migration to map, validate, and commit.",
                        "jobId",
                        job.id().toString(),
                        "totalRows",
                        String.valueOf(job.totalRows()))));
    }

    public ImportJobResponse commit(ImportCommitRequest request) {
        var job = imports.get(request.jobId());
        if ("DETECTED".equals(job.status()) || "MAPPED".equals(job.status())) {
            job = imports.validate(request.jobId());
        }
        if ("VALIDATED".equals(job.status()) || "COMPLETED_WITH_ERRORS".equals(job.status())) {
            job = imports.commit(request.jobId());
        }
        return new ImportJobResponse(
                job.id(),
                job.status(),
                job.fileName(),
                Map.of(
                        "imported", job.successRows(),
                        "skipped", job.skippedRows(),
                        "errors", job.errorRows(),
                        "message", "Delegated to migration pipeline"),
                job.errorSummary());
    }

    @Transactional(readOnly = true)
    public ImportJobResponse getJob(UUID jobId) {
        var job = imports.get(jobId);
        return new ImportJobResponse(
                job.id(),
                job.status(),
                job.fileName(),
                Map.of(
                        "imported", job.successRows(),
                        "skipped", job.skippedRows(),
                        "errors", job.errorRows()),
                job.errorSummary());
    }

    public byte[] exportBarcodes(ExportBarcodesRequest request) {
        var exported = exports.export(new ExportRequest(ImportModule.BARCODE, ExportFormat.CSV));
        return exports.download(exported.id());
    }
}
