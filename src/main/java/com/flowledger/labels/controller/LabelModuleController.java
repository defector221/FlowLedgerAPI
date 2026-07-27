package com.flowledger.labels.controller;

import com.flowledger.labels.dto.LabelDtos.PrintJobRequest;
import com.flowledger.labels.dto.LabelDtos.PrintJobResponse;
import com.flowledger.labels.dto.LabelDtos.RenderRequest;
import com.flowledger.labels.dto.LabelDtos.RenderResponse;
import com.flowledger.labels.dto.LabelDtos.TemplateRequest;
import com.flowledger.labels.dto.LabelDtos.TemplateResponse;
import com.flowledger.labels.service.LabelPrintJobService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/labels")
public class LabelModuleController {
    private final LabelPrintJobService service;

    public LabelModuleController(LabelPrintJobService service) {
        this.service = service;
    }

    @GetMapping("/templates")
    @PreAuthorize("hasAuthority('BARCODE_READ') or hasAuthority('BARCODE_PRINT')")
    public List<TemplateResponse> listTemplates() {
        return service.listTemplates();
    }

    @GetMapping("/templates/{id}")
    @PreAuthorize("hasAuthority('BARCODE_READ') or hasAuthority('BARCODE_PRINT')")
    public TemplateResponse getTemplate(@PathVariable UUID id) {
        return service.getTemplate(id);
    }

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('BARCODE_WRITE')")
    public TemplateResponse createTemplate(@Valid @RequestBody TemplateRequest request) {
        return service.createTemplate(request);
    }

    @PutMapping("/templates/{id}")
    @PreAuthorize("hasAuthority('BARCODE_WRITE')")
    public TemplateResponse updateTemplate(@PathVariable UUID id, @Valid @RequestBody TemplateRequest request) {
        return service.updateTemplate(id, request);
    }

    @DeleteMapping("/templates/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('BARCODE_WRITE')")
    public void deleteTemplate(@PathVariable UUID id) {
        service.deleteTemplate(id);
    }

    @PostMapping("/render")
    @PreAuthorize("hasAuthority('BARCODE_PRINT') or hasAuthority('BARCODE_READ')")
    public ResponseEntity<byte[]> render(@Valid @RequestBody RenderRequest request) {
        RenderResponse response = service.render(request);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=label-preview.pdf")
                .contentType(MediaType.parseMediaType(response.contentType()))
                .body(response.pdfBytes());
    }

    @PostMapping("/print")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('BARCODE_PRINT')")
    public PrintJobResponse queuePrint(@Valid @RequestBody PrintJobRequest request) {
        return service.queuePrint(request);
    }

    @GetMapping("/jobs")
    @PreAuthorize("hasAuthority('BARCODE_PRINT') or hasAuthority('BARCODE_READ')")
    public List<PrintJobResponse> listJobs() {
        return service.listJobs();
    }

    @GetMapping("/jobs/{id}")
    @PreAuthorize("hasAuthority('BARCODE_PRINT') or hasAuthority('BARCODE_READ')")
    public PrintJobResponse getJob(@PathVariable UUID id) {
        return service.getJob(id);
    }
}
