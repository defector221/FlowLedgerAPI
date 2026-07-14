package com.flowledger.marketing.controller;

import com.flowledger.marketing.dto.CampaignDtos.*;
import com.flowledger.marketing.service.MarketingCampaignService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/marketing/campaigns")
public class MarketingCampaignController {
    private final MarketingCampaignService service;

    public MarketingCampaignController(MarketingCampaignService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SALES_MANAGER')")
    public List<CampaignResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SALES_MANAGER')")
    public CampaignResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/{id}/recipients")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SALES_MANAGER')")
    public Page<RecipientResponse> recipients(@PathVariable UUID id, Pageable pageable) {
        return service.listRecipients(id, pageable);
    }

    @PostMapping("/preview-audience")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SALES_MANAGER')")
    public AudiencePreviewResponse previewAudience(@Valid @RequestBody UpsertCampaignRequest request) {
        return service.previewAudience(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SALES_MANAGER')")
    public CampaignResponse create(@Valid @RequestBody UpsertCampaignRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SALES_MANAGER')")
    public CampaignResponse update(@PathVariable UUID id, @Valid @RequestBody UpsertCampaignRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/schedule")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SALES_MANAGER')")
    public CampaignResponse schedule(@PathVariable UUID id, @RequestBody(required = false) ScheduleRequest request) {
        return service.schedule(id, request);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'SALES_MANAGER')")
    public CampaignResponse cancel(@PathVariable UUID id) {
        return service.cancel(id);
    }
}
