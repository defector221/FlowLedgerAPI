package com.flowledger.product.service;

import com.flowledger.common.service.OrganizationScopedService;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.product.dto.ProductIdentificationDtos.ExportBarcodesRequest;
import com.flowledger.product.dto.ProductIdentificationDtos.ImportCommitRequest;
import com.flowledger.product.dto.ProductIdentificationDtos.ImportJobResponse;
import com.flowledger.product.dto.ProductIdentificationDtos.ImportPreviewResponse;
import com.flowledger.product.entity.ProductImportJob;
import com.flowledger.product.repository.ProductImportJobRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class ProductImportExportService extends OrganizationScopedService {
    private final ProductImportJobRepository jobs;

    public ProductImportExportService(ProductImportJobRepository jobs) {
        this.jobs = jobs;
    }

    public ImportPreviewResponse preview(MultipartFile file) {
        ProductImportJob job = new ProductImportJob();
        job.setOrganizationId(orgId());
        job.setStatus("PREVIEW");
        job.setFileName(file == null ? null : file.getOriginalFilename());
        job.setPreviewJson(List.of(Map.of("message", "Import preview stub — validation not implemented")));
        TenantContext.userId().ifPresent(job::setCreatedBy);
        ProductImportJob saved = jobs.save(job);
        return new ImportPreviewResponse(saved.getId(), saved.getStatus(), saved.getPreviewJson());
    }

    public ImportJobResponse commit(ImportCommitRequest request) {
        ProductImportJob job = load(request.jobId());
        job.setStatus("COMMITTED");
        job.setResultJson(Map.of("imported", 0, "skipped", 0, "message", "Import commit stub"));
        job.setCompletedAt(OffsetDateTime.now());
        return toResponse(jobs.save(job));
    }

    @Transactional(readOnly = true)
    public ImportJobResponse getJob(UUID jobId) {
        return toResponse(load(jobId));
    }

    public byte[] exportBarcodes(ExportBarcodesRequest request) {
        String header = "product_id,barcode,status\n";
        return header.getBytes();
    }

    private ProductImportJob load(UUID jobId) {
        return required(jobs.findByIdAndOrganizationId(jobId, orgId()), "Import job");
    }

    private ImportJobResponse toResponse(ProductImportJob job) {
        return new ImportJobResponse(
                job.getId(), job.getStatus(), job.getFileName(), job.getResultJson(), job.getErrorMessage());
    }
}
