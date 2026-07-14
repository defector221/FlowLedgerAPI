package com.flowledger.emailtemplate.controller;

import com.flowledger.emailtemplate.dto.EmailTemplateDtos.PreviewRequest;
import com.flowledger.emailtemplate.dto.EmailTemplateDtos.PreviewResponse;
import com.flowledger.emailtemplate.dto.EmailTemplateDtos.Response;
import com.flowledger.emailtemplate.dto.EmailTemplateDtos.UpsertRequest;
import com.flowledger.emailtemplate.service.EmailTemplateService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/email-templates")
public class EmailTemplateController {
    private final EmailTemplateService service;

    public EmailTemplateController(EmailTemplateService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SALES_MANAGER')")
    public List<Response> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SALES_MANAGER')")
    public Response get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SALES_MANAGER')")
    public Response create(@Valid @RequestBody UpsertRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SALES_MANAGER')")
    public Response update(@PathVariable UUID id, @Valid @RequestBody UpsertRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SALES_MANAGER')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PostMapping("/{id}/preview")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SALES_MANAGER')")
    public PreviewResponse preview(@PathVariable UUID id, @RequestBody(required = false) PreviewRequest request) {
        return service.preview(id, request);
    }
}
