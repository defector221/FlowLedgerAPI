package com.flowledger.platform.service;

import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.organization.entity.OrganizationSettings;
import com.flowledger.organization.repository.OrganizationSettingsRepository;
import com.flowledger.platform.domain.ModuleCodes;
import com.flowledger.platform.dto.PlatformDtos.OrganizationFeatureResponse;
import com.flowledger.platform.dto.PlatformDtos.OrganizationModuleResponse;
import com.flowledger.platform.dto.PlatformDtos.UpsertOrganizationFeatureRequest;
import com.flowledger.platform.dto.PlatformDtos.UpsertOrganizationModuleRequest;
import com.flowledger.platform.entity.ModuleFeature;
import com.flowledger.platform.entity.OrganizationFeature;
import com.flowledger.platform.entity.OrganizationModule;
import com.flowledger.platform.entity.PlatformModule;
import com.flowledger.platform.event.FeatureDisabledEvent;
import com.flowledger.platform.event.FeatureEnabledEvent;
import com.flowledger.platform.event.ModuleDisabledEvent;
import com.flowledger.platform.event.ModuleEnabledEvent;
import com.flowledger.platform.repository.ModuleFeatureRepository;
import com.flowledger.platform.repository.OrganizationFeatureRepository;
import com.flowledger.platform.repository.OrganizationModuleRepository;
import com.flowledger.platform.repository.PlatformModuleRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationModuleService {
    private final OrganizationModuleRepository organizationModules;
    private final OrganizationFeatureRepository organizationFeatures;
    private final PlatformModuleRepository modules;
    private final ModuleFeatureRepository moduleFeatures;
    private final ModuleCatalogService catalog;
    private final ModuleEntitlementCache cache;
    private final OrganizationSettingsRepository settingsRepository;
    private final ApplicationEventPublisher events;

    public OrganizationModuleService(
            OrganizationModuleRepository organizationModules,
            OrganizationFeatureRepository organizationFeatures,
            PlatformModuleRepository modules,
            ModuleFeatureRepository moduleFeatures,
            ModuleCatalogService catalog,
            ModuleEntitlementCache cache,
            OrganizationSettingsRepository settingsRepository,
            ApplicationEventPublisher events) {
        this.organizationModules = organizationModules;
        this.organizationFeatures = organizationFeatures;
        this.modules = modules;
        this.moduleFeatures = moduleFeatures;
        this.catalog = catalog;
        this.cache = cache;
        this.settingsRepository = settingsRepository;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<OrganizationModuleResponse> listModules(UUID organizationId) {
        return organizationModules.findByOrganizationId(organizationId).stream()
                .map(this::toModuleResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<OrganizationFeatureResponse> listFeatures(UUID organizationId) {
        return organizationFeatures.findByOrganizationId(organizationId).stream()
                .map(this::toFeatureResponse)
                .toList();
    }

    private void upsertModule(UUID organizationId, UUID actorId, UpsertOrganizationModuleRequest request) {
        upsertModule(organizationId, actorId, request, true);
    }

    void upsertModule(
            UUID organizationId,
            UUID actorId,
            UpsertOrganizationModuleRequest request,
            boolean validateDependencies) {
        PlatformModule catalogModule = modules
                .findById(request.moduleCode())
                .orElseThrow(() -> new ResourceNotFoundException("Module not found: " + request.moduleCode()));
        if ("COMING_SOON".equalsIgnoreCase(catalogModule.getStatus())
                && Boolean.TRUE.equals(request.enabled())) {
            throw new BusinessException("Module " + request.moduleCode() + " is not available yet");
        }

        OrganizationModule row = organizationModules
                .findByOrganizationIdAndModuleCode(organizationId, request.moduleCode())
                .orElseGet(() -> {
                    OrganizationModule created = new OrganizationModule();
                    created.setOrganizationId(organizationId);
                    created.setModuleCode(request.moduleCode());
                    created.setCreatedBy(actorId);
                    return created;
                });

        boolean wasEnabled = row.getId() != null && row.isEffectivelyEnabled();
        if (request.enabled() != null) {
            if (request.enabled() && validateDependencies) {
                ensureDependenciesEnabled(organizationId, request.moduleCode());
            }
            row.setEnabled(request.enabled());
        }
        if (request.licensed() != null) {
            row.setLicensed(request.licensed());
        }
        if (request.trial() != null) {
            row.setTrial(request.trial());
        }
        if (request.expiresAt() != null) {
            row.setExpiresAt(request.expiresAt());
        }
        if (request.configuration() != null) {
            row.setConfiguration(request.configuration());
        }
        row.setUpdatedBy(actorId);
        organizationModules.save(row);

        if (row.isEffectivelyEnabled()) {
            ensureDefaultFeatures(organizationId, request.moduleCode(), actorId);
        }

        syncSettingsMirror(organizationId, request.moduleCode(), row.isEffectivelyEnabled());

        boolean nowEnabled = row.isEffectivelyEnabled();
        if (!wasEnabled && nowEnabled) {
            events.publishEvent(new ModuleEnabledEvent(organizationId, request.moduleCode()));
        } else if (wasEnabled && !nowEnabled) {
            events.publishEvent(new ModuleDisabledEvent(organizationId, request.moduleCode()));
        }
    }

    @Transactional
    public List<OrganizationModuleResponse> upsertModules(
            UUID organizationId,
            UUID actorId,
            List<UpsertOrganizationModuleRequest> requests,
            boolean validateDependencies) {
        for (UpsertOrganizationModuleRequest request : requests) {
            upsertModule(organizationId, actorId, request, validateDependencies);
        }
        cache.invalidate(organizationId);
        return listModules(organizationId);
    }

    @Transactional
    public List<OrganizationModuleResponse> upsertModules(
            UUID organizationId, UUID actorId, List<UpsertOrganizationModuleRequest> requests) {
        return upsertModules(organizationId, actorId, requests, true);
    }

    @Transactional
    public List<OrganizationFeatureResponse> upsertFeatures(
            UUID organizationId, UUID actorId, List<UpsertOrganizationFeatureRequest> requests) {
        for (UpsertOrganizationFeatureRequest request : requests) {
            upsertFeature(organizationId, actorId, request);
        }
        cache.invalidate(organizationId);
        return listFeatures(organizationId);
    }

    @Transactional
    public void setModuleEnabled(UUID organizationId, String moduleCode, boolean enabled, UUID actorId) {
        upsertModule(
                organizationId,
                actorId,
                new UpsertOrganizationModuleRequest(moduleCode, enabled, null, null, null, null));
        cache.invalidate(organizationId);
    }

    private void upsertFeature(UUID organizationId, UUID actorId, UpsertOrganizationFeatureRequest request) {
        moduleFeatures
                .findByModuleCodeAndFeatureCode(request.moduleCode(), request.featureCode())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Feature not found: " + request.moduleCode() + "." + request.featureCode()));

        if (!Boolean.TRUE.equals(
                cache.get(organizationId).modules().get(request.moduleCode()))) {
            // Reload from DB in case cache stale mid-transaction
            boolean moduleOn = organizationModules
                    .findByOrganizationIdAndModuleCode(organizationId, request.moduleCode())
                    .map(OrganizationModule::isEffectivelyEnabled)
                    .orElse(false);
            if (!moduleOn && Boolean.TRUE.equals(request.enabled())) {
                throw new BusinessException("Enable module " + request.moduleCode() + " before enabling features");
            }
        }

        OrganizationFeature row = organizationFeatures
                .findByOrganizationIdAndModuleCodeAndFeatureCode(
                        organizationId, request.moduleCode(), request.featureCode())
                .orElseGet(() -> {
                    OrganizationFeature created = new OrganizationFeature();
                    created.setOrganizationId(organizationId);
                    created.setModuleCode(request.moduleCode());
                    created.setFeatureCode(request.featureCode());
                    created.setCreatedBy(actorId);
                    return created;
                });

        boolean wasEnabled = row.getId() != null && row.isEffectivelyEnabled();
        if (request.enabled() != null) {
            row.setEnabled(request.enabled());
        }
        if (request.licensed() != null) {
            row.setLicensed(request.licensed());
        }
        if (request.trial() != null) {
            row.setTrial(request.trial());
        }
        if (request.expiresAt() != null) {
            row.setExpiresAt(request.expiresAt());
        }
        if (request.configuration() != null) {
            row.setConfiguration(request.configuration());
        }
        row.setUpdatedBy(actorId);
        organizationFeatures.save(row);

        boolean nowEnabled = row.isEffectivelyEnabled();
        if (!wasEnabled && nowEnabled) {
            events.publishEvent(
                    new FeatureEnabledEvent(organizationId, request.moduleCode(), request.featureCode()));
        } else if (wasEnabled && !nowEnabled) {
            events.publishEvent(
                    new FeatureDisabledEvent(organizationId, request.moduleCode(), request.featureCode()));
        }
    }

    private void ensureDependenciesEnabled(UUID organizationId, String moduleCode) {
        List<String> deps = catalog.dependenciesOf(moduleCode);
        Set<String> missing = new HashSet<>();
        for (String dep : deps) {
            boolean on = organizationModules
                    .findByOrganizationIdAndModuleCode(organizationId, dep)
                    .map(OrganizationModule::isEffectivelyEnabled)
                    .orElse(false);
            if (!on) {
                missing.add(dep);
            }
        }
        if (!missing.isEmpty()) {
            throw new BusinessException(
                    "Module " + moduleCode + " requires enabled dependencies: " + String.join(", ", missing));
        }
    }

    private void ensureDefaultFeatures(UUID organizationId, String moduleCode, UUID actorId) {
        for (ModuleFeature feature : moduleFeatures.findByModuleCodeOrderByFeatureCodeAsc(moduleCode)) {
            organizationFeatures
                    .findByOrganizationIdAndModuleCodeAndFeatureCode(
                            organizationId, moduleCode, feature.getFeatureCode())
                    .orElseGet(() -> {
                        OrganizationFeature created = new OrganizationFeature();
                        created.setOrganizationId(organizationId);
                        created.setModuleCode(moduleCode);
                        created.setFeatureCode(feature.getFeatureCode());
                        created.setEnabled(feature.isEnabledByDefault());
                        created.setLicensed(true);
                        created.setCreatedBy(actorId);
                        created.setUpdatedBy(actorId);
                        return organizationFeatures.save(created);
                    });
        }
    }

    private void syncSettingsMirror(UUID organizationId, String moduleCode, boolean enabled) {
        if (!ModuleCodes.RETAIL.equals(moduleCode) && !ModuleCodes.TRANSPORT.equals(moduleCode)) {
            return;
        }
        OrganizationSettings settings = settingsRepository
                .findByOrganizationId(organizationId)
                .orElse(null);
        if (settings == null) {
            return;
        }
        if (ModuleCodes.RETAIL.equals(moduleCode)) {
            settings.setRetailEnabled(enabled);
        } else {
            settings.setTransportEnabled(enabled);
        }
        settingsRepository.save(settings);
    }

    private OrganizationModuleResponse toModuleResponse(OrganizationModule row) {
        return new OrganizationModuleResponse(
                row.getModuleCode(),
                row.isEnabled(),
                row.isLicensed(),
                row.isTrial(),
                row.getExpiresAt(),
                row.getConfiguration(),
                row.isEffectivelyEnabled());
    }

    private OrganizationFeatureResponse toFeatureResponse(OrganizationFeature row) {
        return new OrganizationFeatureResponse(
                row.getModuleCode(),
                row.getFeatureCode(),
                row.isEnabled(),
                row.isLicensed(),
                row.isTrial(),
                row.getExpiresAt(),
                row.getConfiguration(),
                row.isEffectivelyEnabled());
    }
}
