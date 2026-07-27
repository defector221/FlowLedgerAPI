package com.flowledger.migration.api;

import com.flowledger.common.dto.ApiResponse;
import com.flowledger.common.dto.PageResponse;
import com.flowledger.migration.domain.ImportModule;
import com.flowledger.migration.dto.MigrationDtos.*;
import com.flowledger.migration.export.ExportEngine;
import com.flowledger.migration.job.ImportJobService;
import com.flowledger.migration.mapping.MappingProfileService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/migration")
public class MigrationController {
    private final ImportJobService imports;
    private final MappingProfileService profiles;
    private final ExportEngine exports;

    public MigrationController(
            ImportJobService imports, MappingProfileService profiles, ExportEngine exports) {
        this.imports = imports;
        this.profiles = profiles;
        this.exports = exports;
    }

    @GetMapping("/modules")
    @PreAuthorize("hasAuthority('MIGRATION_READ')")
    public ApiResponse<List<ModuleInfo>> modules() {
        return ApiResponse.of(imports.modules());
    }

    @GetMapping("/sources")
    @PreAuthorize("hasAuthority('MIGRATION_READ')")
    public ApiResponse<List<SourceInfo>> sources() {
        return ApiResponse.of(imports.sources());
    }

    @PostMapping(value = "/import/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MIGRATION_WRITE')")
    public ApiResponse<ImportJobResponse> upload(
            @RequestParam ImportModule module, @RequestPart("file") MultipartFile file) {
        return ApiResponse.of(imports.upload(module, file));
    }

    @PostMapping("/import/{jobId}/detect")
    @PreAuthorize("hasAuthority('MIGRATION_WRITE')")
    public ApiResponse<ImportJobResponse> detect(@PathVariable UUID jobId) {
        return ApiResponse.of(imports.detect(jobId));
    }

    @PutMapping("/import/{jobId}/mapping")
    @PreAuthorize("hasAuthority('MIGRATION_WRITE')")
    public ApiResponse<ImportJobResponse> mapping(
            @PathVariable UUID jobId, @Valid @RequestBody MappingUpdateRequest request) {
        return ApiResponse.of(imports.saveMapping(jobId, request));
    }

    @PostMapping("/import/{jobId}/validate")
    @PreAuthorize("hasAuthority('MIGRATION_WRITE')")
    public ApiResponse<ImportJobResponse> validate(@PathVariable UUID jobId) {
        return ApiResponse.of(imports.validate(jobId));
    }

    @GetMapping("/import/{jobId}/preview")
    @PreAuthorize("hasAuthority('MIGRATION_READ')")
    public ApiResponse<PageResponse<PreviewRow>> preview(@PathVariable UUID jobId, Pageable pageable) {
        return ApiResponse.of(imports.preview(jobId, pageable));
    }

    @PatchMapping("/import/{jobId}/rows/{rowNo}")
    @PreAuthorize("hasAuthority('MIGRATION_WRITE')")
    public ApiResponse<PreviewRow> fixRow(
            @PathVariable UUID jobId, @PathVariable int rowNo, @RequestBody RowFixRequest request) {
        return ApiResponse.of(imports.fixRow(jobId, rowNo, request));
    }

    @PostMapping("/import/{jobId}/commit")
    @PreAuthorize("hasAuthority('MIGRATION_WRITE')")
    public ApiResponse<ImportJobResponse> commit(@PathVariable UUID jobId) {
        return ApiResponse.of(imports.commit(jobId));
    }

    @GetMapping("/import/{jobId}")
    @PreAuthorize("hasAuthority('MIGRATION_READ')")
    public ApiResponse<ImportJobResponse> get(@PathVariable UUID jobId) {
        return ApiResponse.of(imports.get(jobId));
    }

    @GetMapping("/import/{jobId}/report")
    @PreAuthorize("hasAuthority('MIGRATION_READ')")
    public ApiResponse<ImportReportResponse> report(@PathVariable UUID jobId) {
        return ApiResponse.of(imports.report(jobId));
    }

    @GetMapping("/import/{jobId}/errors.csv")
    @PreAuthorize("hasAuthority('MIGRATION_READ')")
    public ResponseEntity<byte[]> errorCsv(@PathVariable UUID jobId) {
        byte[] csv = imports.errorCsv(jobId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"import-errors.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    @GetMapping("/import/history")
    @PreAuthorize("hasAuthority('MIGRATION_READ')")
    public ApiResponse<PageResponse<ImportJobResponse>> history(Pageable pageable) {
        return ApiResponse.of(imports.history(pageable));
    }

    @GetMapping("/mapping-profiles")
    @PreAuthorize("hasAuthority('MIGRATION_READ')")
    public ApiResponse<List<MappingProfileResponse>> listProfiles(
            @RequestParam(required = false) String module) {
        return ApiResponse.of(profiles.list(module));
    }

    @PostMapping("/mapping-profiles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MIGRATION_WRITE')")
    public ApiResponse<MappingProfileResponse> createProfile(@Valid @RequestBody MappingProfileRequest request) {
        return ApiResponse.of(profiles.create(request));
    }

    @PostMapping("/export")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('MIGRATION_EXPORT')")
    public ApiResponse<ExportJobResponse> export(@Valid @RequestBody ExportRequest request) {
        return ApiResponse.of(exports.export(request));
    }

    @GetMapping("/export/{jobId}")
    @PreAuthorize("hasAuthority('MIGRATION_EXPORT') or hasAuthority('MIGRATION_READ')")
    public ApiResponse<ExportJobResponse> getExport(@PathVariable UUID jobId) {
        return ApiResponse.of(exports.get(jobId));
    }

    @GetMapping("/export/{jobId}/download")
    @PreAuthorize("hasAuthority('MIGRATION_EXPORT') or hasAuthority('MIGRATION_READ')")
    public ResponseEntity<byte[]> downloadExport(@PathVariable UUID jobId) {
        ExportJobResponse meta = exports.get(jobId);
        byte[] body = exports.download(jobId);
        String name = meta.fileName() == null ? "export.bin" : meta.fileName();
        MediaType type = name.endsWith(".xlsx")
                ? MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                : MediaType.parseMediaType("text/csv");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(type)
                .body(body);
    }

    @GetMapping("/templates/{module}")
    @PreAuthorize("hasAuthority('MIGRATION_READ')")
    public ResponseEntity<byte[]> template(@PathVariable ImportModule module) {
        byte[] body = exports.template(module);
        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + module.name().toLowerCase() + "-template.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(body);
    }
}
