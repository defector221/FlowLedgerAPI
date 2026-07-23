package com.flowledger.platform.controller;

import com.flowledger.common.dto.ApiResponse;
import com.flowledger.common.security.SecurityUtils;
import com.flowledger.platform.dto.PlatformDtos.*;
import com.flowledger.platform.service.CapabilitiesService;
import com.flowledger.platform.service.EditionService;
import com.flowledger.platform.service.ModuleCatalogService;
import com.flowledger.platform.service.OrganizationModuleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform")
public class PlatformController {
    private final ModuleCatalogService catalog;
    private final EditionService editions;
    private final OrganizationModuleService organizationModules;
    private final CapabilitiesService capabilities;

    public PlatformController(
            ModuleCatalogService catalog,
            EditionService editions,
            OrganizationModuleService organizationModules,
            CapabilitiesService capabilities) {
        this.catalog = catalog;
        this.editions = editions;
        this.organizationModules = organizationModules;
        this.capabilities = capabilities;
    }

    @GetMapping("/modules")
    ApiResponse<List<ModuleResponse>> modules() {
        return ApiResponse.of(catalog.listModules());
    }

    @GetMapping("/modules/{code}/features")
    ApiResponse<List<ModuleFeatureResponse>> moduleFeatures(@PathVariable String code) {
        return ApiResponse.of(catalog.listFeatures(code.toUpperCase()));
    }

    @GetMapping("/modules/features")
    ApiResponse<List<ModuleFeatureResponse>> allFeatures() {
        return ApiResponse.of(catalog.listAllFeatures());
    }

    @GetMapping("/editions")
    ApiResponse<List<EditionResponse>> listEditions() {
        return ApiResponse.of(editions.listEditions());
    }

    @GetMapping("/organization/edition")
    ApiResponse<OrganizationEditionResponse> currentEdition() {
        return ApiResponse.of(editions.currentEdition(SecurityUtils.currentOrganizationId()));
    }

    @PatchMapping("/organization/edition")
    @PreAuthorize("hasRole('ORGANIZATION_ADMIN')")
    ApiResponse<OrganizationEditionResponse> changeEdition(@Valid @RequestBody UpdateEditionRequest request) {
        return ApiResponse.of(editions.changeEdition(
                SecurityUtils.currentOrganizationId(),
                request.editionCode().toUpperCase(),
                SecurityUtils.currentUserId()));
    }

    @GetMapping("/organization/modules")
    ApiResponse<List<OrganizationModuleResponse>> orgModules() {
        return ApiResponse.of(organizationModules.listModules(SecurityUtils.currentOrganizationId()));
    }

    @PutMapping("/organization/modules")
    @PreAuthorize("hasRole('ORGANIZATION_ADMIN')")
    ApiResponse<List<OrganizationModuleResponse>> updateOrgModules(
            @Valid @RequestBody UpsertOrganizationModulesRequest request) {
        UUID orgId = SecurityUtils.currentOrganizationId();
        UUID userId = SecurityUtils.currentUserId();
        List<UpsertOrganizationModuleRequest> normalized = request.modules().stream()
                .map(m -> new UpsertOrganizationModuleRequest(
                        m.moduleCode().toUpperCase(),
                        m.enabled(),
                        m.licensed(),
                        m.trial(),
                        m.expiresAt(),
                        m.configuration()))
                .toList();
        return ApiResponse.of(organizationModules.upsertModules(orgId, userId, normalized));
    }

    @GetMapping("/organization/features")
    ApiResponse<List<OrganizationFeatureResponse>> orgFeatures() {
        return ApiResponse.of(organizationModules.listFeatures(SecurityUtils.currentOrganizationId()));
    }

    @PutMapping("/organization/features")
    @PreAuthorize("hasRole('ORGANIZATION_ADMIN')")
    ApiResponse<List<OrganizationFeatureResponse>> updateOrgFeatures(
            @Valid @RequestBody UpsertOrganizationFeaturesRequest request) {
        UUID orgId = SecurityUtils.currentOrganizationId();
        UUID userId = SecurityUtils.currentUserId();
        List<UpsertOrganizationFeatureRequest> normalized = request.features().stream()
                .map(f -> new UpsertOrganizationFeatureRequest(
                        f.moduleCode().toUpperCase(),
                        f.featureCode().toUpperCase(),
                        f.enabled(),
                        f.licensed(),
                        f.trial(),
                        f.expiresAt(),
                        f.configuration()))
                .toList();
        return ApiResponse.of(organizationModules.upsertFeatures(orgId, userId, normalized));
    }

    @GetMapping("/organization/capabilities")
    ApiResponse<CapabilitiesResponse> capabilities() {
        return ApiResponse.of(capabilities.capabilities(SecurityUtils.currentOrganizationId()));
    }
}
