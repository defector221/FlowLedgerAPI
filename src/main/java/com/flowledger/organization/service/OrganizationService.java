package com.flowledger.organization.service;

import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.common.security.SecurityUtils;
import com.flowledger.organization.dto.*;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.entity.OrganizationSettings;
import com.flowledger.organization.mapper.OrganizationMapper;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.organization.repository.OrganizationSettingsRepository;
import com.flowledger.storage.StorageService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.UUID;

@Service
public class OrganizationService {
    private final OrganizationRepository repo;
    private final OrganizationSettingsRepository settingsRepo;
    private final OrganizationMapper mapper;
    private final StorageService storage;

    public OrganizationService(
            OrganizationRepository repo,
            OrganizationSettingsRepository settingsRepo,
            OrganizationMapper mapper,
            StorageService storage
    ) {
        this.repo = repo;
        this.settingsRepo = settingsRepo;
        this.mapper = mapper;
        this.storage = storage;
    }

    public OrganizationResponse current() {
        return mapper.toResponse(get(SecurityUtils.currentOrganizationId()));
    }

    @Transactional
    public OrganizationResponse update(UUID id, UpdateOrganizationRequest request) {
        Organization organization = get(id);
        mapper.update(request, organization);
        return mapper.toResponse(repo.save(organization));
    }

    @Transactional
    public String uploadLogo(UUID id, MultipartFile file) {
        Organization organization = get(id);
        String key = "organizations/" + id + "/logo/" + UUID.randomUUID() + "-" + file.getOriginalFilename();
        storage.store(key, file);
        organization.setLogoObjectKey(key);
        repo.save(organization);
        return key;
    }

    @Transactional
    public OrganizationResponse completeOnboarding(UUID id) {
        Organization organization = get(id);
        organization.setOnboardingCompleted(true);
        organization.setOnboardingCompletedAt(Instant.now());
        return mapper.toResponse(repo.save(organization));
    }

    @Transactional(readOnly = true)
    public OrganizationSettingsResponse settings() {
        UUID orgId = SecurityUtils.currentOrganizationId();
        OrganizationSettings settings = settingsRepo.findByOrganizationId(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization settings not found"));
        return mapper.toResponse(settings);
    }

    @Transactional
    public OrganizationSettingsResponse updateSettings(UpdateOrganizationSettingsRequest request) {
        UUID orgId = SecurityUtils.currentOrganizationId();
        OrganizationSettings settings = settingsRepo.findByOrganizationId(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization settings not found"));
        if (request.inventoryDeductionEvent() != null) {
            settings.setInventoryDeductionEvent(request.inventoryDeductionEvent());
        }
        if (request.taxInclusiveDefault() != null) {
            settings.setTaxInclusiveDefault(request.taxInclusiveDefault());
        }
        if (request.roundOffEnabled() != null) {
            settings.setRoundOffEnabled(request.roundOffEnabled());
        }
        if (request.defaultWarehouseId() != null) {
            settings.setDefaultWarehouseId(request.defaultWarehouseId());
        }
        if (request.settingsJson() != null) {
            settings.setSettingsJson(request.settingsJson());
        }
        return mapper.toResponse(settingsRepo.save(settings));
    }

    private Organization get(UUID id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
    }
}
