package com.flowledger.platform.service;

import com.flowledger.platform.entity.OrganizationFeature;
import com.flowledger.platform.entity.OrganizationModule;
import com.flowledger.platform.repository.OrganizationFeatureRepository;
import com.flowledger.platform.repository.OrganizationModuleRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * In-memory entitlement snapshot cache keyed by organization id.
 */
@Service
public class ModuleEntitlementCache {
    public record Snapshot(Map<String, Boolean> modules, Map<String, Boolean> features) {}

    private final OrganizationModuleRepository organizationModules;
    private final OrganizationFeatureRepository organizationFeatures;
    private final ConcurrentHashMap<UUID, Snapshot> cache = new ConcurrentHashMap<>();

    public ModuleEntitlementCache(
            OrganizationModuleRepository organizationModules, OrganizationFeatureRepository organizationFeatures) {
        this.organizationModules = organizationModules;
        this.organizationFeatures = organizationFeatures;
    }

    public Snapshot get(UUID organizationId) {
        return cache.computeIfAbsent(organizationId, this::load);
    }

    public void invalidate(UUID organizationId) {
        cache.remove(organizationId);
    }

    public void invalidateAll() {
        cache.clear();
    }

    private Snapshot load(UUID organizationId) {
        List<OrganizationModule> modules = organizationModules.findByOrganizationId(organizationId);
        List<OrganizationFeature> features = organizationFeatures.findByOrganizationId(organizationId);
        Map<String, Boolean> moduleMap = modules.stream()
                .collect(Collectors.toMap(OrganizationModule::getModuleCode, OrganizationModule::isEffectivelyEnabled));
        Map<String, Boolean> featureMap = features.stream()
                .collect(Collectors.toMap(
                        f -> f.getModuleCode() + "." + f.getFeatureCode(),
                        OrganizationFeature::isEffectivelyEnabled,
                        (a, b) -> a));
        return new Snapshot(moduleMap, featureMap);
    }
}
