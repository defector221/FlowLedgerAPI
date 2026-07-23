package com.flowledger.platform.service;

import com.flowledger.platform.dto.PlatformDtos.CapabilitiesResponse;
import com.flowledger.platform.dto.PlatformDtos.OrganizationFeatureResponse;
import com.flowledger.platform.dto.PlatformDtos.OrganizationModuleResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CapabilitiesService {
    private final EditionService editionService;
    private final OrganizationModuleService organizationModuleService;
    private final ModuleEntitlementCache cache;

    public CapabilitiesService(
            EditionService editionService,
            OrganizationModuleService organizationModuleService,
            ModuleEntitlementCache cache) {
        this.editionService = editionService;
        this.organizationModuleService = organizationModuleService;
        this.cache = cache;
    }

    @Transactional(readOnly = true)
    public CapabilitiesResponse capabilities(UUID organizationId) {
        String editionCode = editionService.currentEdition(organizationId).editionCode();
        List<OrganizationModuleResponse> moduleDetails = organizationModuleService.listModules(organizationId);
        List<OrganizationFeatureResponse> featureDetails = organizationModuleService.listFeatures(organizationId);

        // Prefer fresh DB entitlement rows over cache — putIfAbsent previously kept stale
        // true/false after toggles when the in-memory snapshot lagged.
        Map<String, Boolean> modules = new LinkedHashMap<>();
        Map<String, Boolean> features = new LinkedHashMap<>();
        for (OrganizationModuleResponse m : moduleDetails) {
            modules.put(m.moduleCode(), m.effectivelyEnabled());
        }
        for (OrganizationFeatureResponse f : featureDetails) {
            features.put(f.moduleCode() + "." + f.featureCode(), f.effectivelyEnabled());
        }

        // Fill any keys present only in cache (should be rare after invalidate)
        ModuleEntitlementCache.Snapshot snapshot = cache.get(organizationId);
        snapshot.modules().forEach(modules::putIfAbsent);
        snapshot.features().forEach(features::putIfAbsent);

        return new CapabilitiesResponse(editionCode, modules, features, moduleDetails, featureDetails);
    }
}
