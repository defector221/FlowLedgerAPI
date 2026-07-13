package com.flowledger.template.controller;

import com.flowledger.template.dto.TemplateDtos.Preset;
import com.flowledger.template.dto.TemplateDtos.TemplatePreviewRequest;
import com.flowledger.template.dto.TemplateDtos.TemplateRequest;
import com.flowledger.template.entity.InvoiceTemplate;
import com.flowledger.template.service.InvoiceTemplateService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/templates")
public class InvoiceTemplateController {
    private final InvoiceTemplateService service;

    public InvoiceTemplateController(InvoiceTemplateService s) {
        service = s;
    }

    @GetMapping("/presets")
    public List<Preset> presets() {
        return service.presets();
    }

    @GetMapping
    public List<InvoiceTemplate> list(@RequestParam(required = false) String documentType) {
        return service.list(documentType);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceTemplate create(@RequestBody TemplateRequest r) {
        return service.create(r);
    }

    @PostMapping("/preview")
    public ResponseEntity<byte[]> preview(@RequestBody TemplatePreviewRequest r) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=template-preview.pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(service.preview(r));
    }

    @GetMapping("/{id}")
    public InvoiceTemplate get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    public InvoiceTemplate update(@PathVariable UUID id, @RequestBody TemplateRequest r) {
        return service.update(id, r);
    }

    @PostMapping("/{id}/default")
    public InvoiceTemplate defaultTemplate(@PathVariable UUID id) {
        return service.setDefault(id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
