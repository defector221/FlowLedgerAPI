package com.flowledger.retail.controller;

import static com.flowledger.retail.dto.RetailDtos.*;

import com.flowledger.retail.service.RetailLabelService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/retail/labels")
public class RetailLabelController {
    private final RetailLabelService service;

    public RetailLabelController(RetailLabelService service) {
        this.service = service;
    }

    @GetMapping("/templates")
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public List<LabelTemplateResponse> listTemplates() {
        return service.listTemplates();
    }

    @GetMapping("/templates/{id}")
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public LabelTemplateResponse getTemplate(@PathVariable UUID id) {
        return service.getTemplate(id);
    }

    @PostMapping("/templates")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public LabelTemplateResponse createTemplate(@Valid @RequestBody LabelTemplateRequest r) {
        return service.createTemplate(r);
    }

    @PutMapping("/templates/{id}")
    @PreAuthorize("hasAuthority('RETAIL_STORE_MANAGE')")
    public LabelTemplateResponse updateTemplate(@PathVariable UUID id, @Valid @RequestBody LabelTemplateRequest r) {
        return service.updateTemplate(id, r);
    }

    @DeleteMapping("/templates/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('RETAIL_ADMIN')")
    public void deleteTemplate(@PathVariable UUID id) {
        service.deleteTemplate(id);
    }

    @PostMapping("/render")
    @PreAuthorize("hasAuthority('RETAIL_VIEW')")
    public RenderLabelResponse renderLabel(@Valid @RequestBody RenderLabelRequest r) {
        return service.renderLabel(r);
    }
}
