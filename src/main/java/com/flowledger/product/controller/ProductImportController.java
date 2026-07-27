package com.flowledger.product.controller;

import com.flowledger.product.dto.ProductIdentificationDtos.ExportBarcodesRequest;
import com.flowledger.product.dto.ProductIdentificationDtos.ImportCommitRequest;
import com.flowledger.product.dto.ProductIdentificationDtos.ImportJobResponse;
import com.flowledger.product.dto.ProductIdentificationDtos.ImportPreviewResponse;
import com.flowledger.product.service.ProductImportExportService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/products")
public class ProductImportController {
    private final ProductImportExportService service;

    public ProductImportController(ProductImportExportService service) {
        this.service = service;
    }

    @PostMapping(value = "/import/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('BARCODE_WRITE') or hasAuthority('PRODUCT_WRITE')")
    public ImportPreviewResponse preview(@RequestPart("file") MultipartFile file) {
        return service.preview(file);
    }

    @PostMapping("/import/commit")
    @PreAuthorize("hasAuthority('BARCODE_WRITE') or hasAuthority('PRODUCT_WRITE')")
    public ImportJobResponse commit(@Valid @RequestBody ImportCommitRequest request) {
        return service.commit(request);
    }

    @GetMapping("/import/{jobId}")
    @PreAuthorize("hasAuthority('BARCODE_READ') or hasAuthority('PRODUCT_READ')")
    public ImportJobResponse getJob(@PathVariable UUID jobId) {
        return service.getJob(jobId);
    }

    @PostMapping("/barcodes/export")
    @PreAuthorize("hasAuthority('BARCODE_READ') or hasAuthority('PRODUCT_READ')")
    public ResponseEntity<byte[]> exportBarcodes(@RequestBody(required = false) ExportBarcodesRequest request) {
        byte[] body = service.exportBarcodes(request == null ? new ExportBarcodesRequest(null, "csv") : request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=barcodes.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(body);
    }
}
