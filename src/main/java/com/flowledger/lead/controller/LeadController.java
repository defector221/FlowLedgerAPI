package com.flowledger.lead.controller;

import com.flowledger.lead.dto.LeadDtos.*;
import com.flowledger.lead.service.LeadService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/leads")
public class LeadController {
    private final LeadService service;

    public LeadController(LeadService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SALES_MANAGER')")
    public Response create(@Valid @RequestBody Create dto) {
        return service.create(dto);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SALES_MANAGER')")
    public Response get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SALES_MANAGER')")
    public Response update(@PathVariable UUID id, @Valid @RequestBody Update dto) {
        return service.update(id, dto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SALES_MANAGER')")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SALES_MANAGER')")
    public Page<Response> list(@RequestParam(required = false) String status, Pageable pageable) {
        return service.list(status, pageable);
    }

    @PostMapping("/{id}/follow-ups")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SALES_MANAGER')")
    public FollowUpResponse addFollowUp(@PathVariable UUID id, @Valid @RequestBody FollowUpCreate dto) {
        return service.addFollowUp(id, dto);
    }

    @GetMapping("/{id}/follow-ups")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SALES_MANAGER')")
    public List<FollowUpResponse> listFollowUps(@PathVariable UUID id) {
        return service.listFollowUps(id);
    }

    @PostMapping("/{id}/follow-ups/{followUpId}/complete")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SALES_MANAGER')")
    public FollowUpResponse completeFollowUp(@PathVariable UUID id, @PathVariable UUID followUpId) {
        return service.completeFollowUp(id, followUpId);
    }

    @PostMapping("/{id}/convert")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SALES_MANAGER')")
    public Response convert(@PathVariable UUID id, @RequestBody(required = false) ConvertRequest request) {
        return service.convert(id, request);
    }
}
