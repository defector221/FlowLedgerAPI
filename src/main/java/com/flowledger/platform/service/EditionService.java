package com.flowledger.platform.service;

import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.platform.domain.EditionCodes;
import com.flowledger.platform.dto.PlatformDtos.EditionResponse;
import com.flowledger.platform.dto.PlatformDtos.OrganizationEditionResponse;
import com.flowledger.platform.dto.PlatformDtos.UpsertOrganizationModuleRequest;
import com.flowledger.platform.entity.Edition;
import com.flowledger.platform.entity.EditionModule;
import com.flowledger.platform.entity.OrganizationModule;
import com.flowledger.platform.event.EditionChangedEvent;
import com.flowledger.platform.repository.EditionModuleRepository;
import com.flowledger.platform.repository.EditionRepository;
import com.flowledger.platform.repository.OrganizationModuleRepository;
import com.flowledger.platform.repository.PlanEditionDefaultRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EditionService {
    private final EditionRepository editions;
    private final EditionModuleRepository editionModules;
    private final OrganizationRepository organizations;
    private final OrganizationModuleRepository organizationModules;
    private final OrganizationModuleService organizationModuleService;
    private final PlanEditionDefaultRepository planEditionDefaults;
    private final ModuleEntitlementCache cache;
    private final ApplicationEventPublisher events;

    public EditionService(
            EditionRepository editions,
            EditionModuleRepository editionModules,
            OrganizationRepository organizations,
            OrganizationModuleRepository organizationModules,
            OrganizationModuleService organizationModuleService,
            PlanEditionDefaultRepository planEditionDefaults,
            ModuleEntitlementCache cache,
            ApplicationEventPublisher events) {
        this.editions = editions;
        this.editionModules = editionModules;
        this.organizations = organizations;
        this.organizationModules = organizationModules;
        this.organizationModuleService = organizationModuleService;
        this.planEditionDefaults = planEditionDefaults;
        this.cache = cache;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public List<EditionResponse> listEditions() {
        return editions.findAllByOrderByRankAsc().stream()
                .map(e -> new EditionResponse(
                        e.getCode(),
                        e.getDisplayName(),
                        e.getDescription(),
                        e.getRank(),
                        editionModules.findByEditionCode(e.getCode()).stream()
                                .map(EditionModule::getModuleCode)
                                .toList()))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrganizationEditionResponse currentEdition(UUID organizationId) {
        Organization org = organizations
                .findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
        Edition edition = editions
                .findById(org.getEditionCode())
                .orElseThrow(() -> new ResourceNotFoundException("Edition not found"));
        return new OrganizationEditionResponse(edition.getCode(), edition.getDisplayName(), edition.getDescription());
    }

    @Transactional
    public OrganizationEditionResponse changeEdition(UUID organizationId, String editionCode, UUID actorId) {
        Edition edition = editions
                .findById(editionCode)
                .orElseThrow(() -> new ResourceNotFoundException("Edition not found: " + editionCode));
        Organization org = organizations
                .findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
        String previous = org.getEditionCode();
        org.setEditionCode(edition.getCode());
        organizations.save(org);

        if (!EditionCodes.CUSTOM.equalsIgnoreCase(edition.getCode())) {
            applyEditionModules(organizationId, edition.getCode(), actorId);
        }

        cache.invalidate(organizationId);
        events.publishEvent(new EditionChangedEvent(organizationId, previous, edition.getCode()));
        return new OrganizationEditionResponse(edition.getCode(), edition.getDisplayName(), edition.getDescription());
    }

    /**
     * Seeds entitlements for a newly created organization based on subscription plan → edition map.
     */
    @Transactional
    public void provisionNewOrganization(UUID organizationId, String planCode, UUID actorId) {
        String editionCode = planEditionDefaults
                .findById(planCode == null ? "FREE" : planCode.toUpperCase())
                .map(p -> p.getEditionCode())
                .orElse(EditionCodes.PROFESSIONAL);
        Organization org = organizations
                .findById(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
        org.setEditionCode(editionCode);
        organizations.save(org);
        applyEditionModules(organizationId, editionCode, actorId);
        // Preserve historical UX: transport on by default for new orgs under PROFESSIONAL+
        cache.invalidate(organizationId);
    }

    private void applyEditionModules(UUID organizationId, String editionCode, UUID actorId) {
        if (EditionCodes.CUSTOM.equalsIgnoreCase(editionCode)) {
            throw new BusinessException("CUSTOM edition does not auto-apply modules");
        }
        Set<String> included = new HashSet<>();
        editionModules.findByEditionCode(editionCode).forEach(em -> included.add(em.getModuleCode()));

        List<UpsertOrganizationModuleRequest> requests = included.stream()
                .map(code -> new UpsertOrganizationModuleRequest(code, true, true, false, null, null))
                .toList();
        organizationModuleService.upsertModules(organizationId, actorId, requests, false);

        // Disable modules that are present but not in edition (keep core never stripped if still in edition)
        for (OrganizationModule existing : organizationModules.findByOrganizationId(organizationId)) {
            if (!included.contains(existing.getModuleCode()) && existing.isEnabled()) {
                organizationModuleService.setModuleEnabled(organizationId, existing.getModuleCode(), false, actorId);
            }
        }
    }
}
