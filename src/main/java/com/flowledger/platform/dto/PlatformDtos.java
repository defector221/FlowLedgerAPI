package com.flowledger.platform.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class PlatformDtos {
    private PlatformDtos() {}

    public record ModuleResponse(
            String code,
            String displayName,
            String description,
            String icon,
            String category,
            String version,
            boolean core,
            boolean enabledByDefault,
            String status,
            List<String> dependencies) {}

    public record ModuleFeatureResponse(
            String moduleCode,
            String featureCode,
            String displayName,
            String description,
            boolean enabledByDefault,
            String status) {}

    public record EditionResponse(
            String code, String displayName, String description, int rank, List<String> moduleCodes) {}

    public record OrganizationModuleResponse(
            String moduleCode,
            boolean enabled,
            boolean licensed,
            boolean trial,
            Instant expiresAt,
            String configuration,
            boolean effectivelyEnabled) {}

    public record OrganizationFeatureResponse(
            String moduleCode,
            String featureCode,
            boolean enabled,
            boolean licensed,
            boolean trial,
            Instant expiresAt,
            String configuration,
            boolean effectivelyEnabled) {}

    public record OrganizationEditionResponse(String editionCode, String displayName, String description) {}

    public record UpdateEditionRequest(String editionCode) {}

    public record UpsertOrganizationModuleRequest(
            String moduleCode,
            Boolean enabled,
            Boolean licensed,
            Boolean trial,
            Instant expiresAt,
            String configuration) {}

    public record UpsertOrganizationModulesRequest(List<UpsertOrganizationModuleRequest> modules) {}

    public record UpsertOrganizationFeatureRequest(
            String moduleCode,
            String featureCode,
            Boolean enabled,
            Boolean licensed,
            Boolean trial,
            Instant expiresAt,
            String configuration) {}

    public record UpsertOrganizationFeaturesRequest(List<UpsertOrganizationFeatureRequest> features) {}

    public record CapabilitiesResponse(
            String editionCode,
            Map<String, Boolean> modules,
            Map<String, Boolean> features,
            List<OrganizationModuleResponse> moduleDetails,
            List<OrganizationFeatureResponse> featureDetails) {}
}
