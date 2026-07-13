package com.flowledger.organization.controller;

import com.flowledger.common.dto.ApiResponse;
import com.flowledger.common.security.SecurityUtils;
import com.flowledger.organization.dto.*;
import com.flowledger.organization.service.OrganizationService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {
    private final OrganizationService service;

    public OrganizationController(OrganizationService service) {
        this.service = service;
    }

    @GetMapping("/current")
    ApiResponse<OrganizationResponse> current() {
        return ApiResponse.of(service.current());
    }

    @PutMapping("/current")
    @PreAuthorize("hasRole('ORGANIZATION_ADMIN')")
    ApiResponse<OrganizationResponse> update(@Valid @RequestBody UpdateOrganizationRequest request) {
        return ApiResponse.of(service.update(SecurityUtils.currentOrganizationId(), request));
    }

    @PostMapping("/current/logo")
    @PreAuthorize("hasRole('ORGANIZATION_ADMIN')")
    ApiResponse<String> logo(@RequestParam("file") MultipartFile file) {
        return ApiResponse.of(service.uploadLogo(SecurityUtils.currentOrganizationId(), file));
    }

    @PostMapping("/current/complete-onboarding")
    @PreAuthorize("hasRole('ORGANIZATION_ADMIN')")
    ApiResponse<OrganizationResponse> completeOnboarding() {
        return ApiResponse.of(service.completeOnboarding(SecurityUtils.currentOrganizationId()));
    }

    @GetMapping("/current/settings")
    ApiResponse<OrganizationSettingsResponse> settings() {
        return ApiResponse.of(service.settings());
    }

    @PutMapping("/current/settings")
    @PreAuthorize("hasRole('ORGANIZATION_ADMIN')")
    ApiResponse<OrganizationSettingsResponse> updateSettings(
            @Valid @RequestBody UpdateOrganizationSettingsRequest request) {
        return ApiResponse.of(service.updateSettings(request));
    }
}
