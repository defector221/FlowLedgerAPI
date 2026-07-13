package com.flowledger.marketing.controller;

import com.flowledger.marketing.dto.MarketingDtos.*;
import com.flowledger.marketing.service.MarketingSequenceService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/marketing")
public class MarketingController {
    private final MarketingSequenceService service;

    public MarketingController(MarketingSequenceService service) {
        this.service = service;
    }

    @GetMapping("/sequences")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SALES_MANAGER')")
    public List<SequenceResponse> listSequences() {
        return service.list();
    }

    @PostMapping("/sequences")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SALES_MANAGER')")
    public SequenceResponse createSequence(@Valid @RequestBody CreateSequence dto) {
        return service.create(dto);
    }

    @GetMapping("/sequences/{id}")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SALES_MANAGER')")
    public SequenceResponse getSequence(@PathVariable UUID id) {
        return service.get(id);
    }

    @PostMapping("/sequences/{id}/enroll/{leadId}")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SALES_MANAGER')")
    public EnrollmentResponse enroll(@PathVariable UUID id, @PathVariable UUID leadId) {
        return service.enrollLead(id, leadId);
    }

    @PostMapping("/enrollments/{id}/cancel")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SALES_MANAGER')")
    public EnrollmentResponse cancelEnrollment(@PathVariable UUID id) {
        return service.cancelEnrollment(id);
    }
}
