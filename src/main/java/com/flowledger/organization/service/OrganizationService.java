package com.flowledger.organization.service;

import com.flowledger.accounting.service.ChartOfAccountsBootstrapService;
import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.common.security.SecurityUtils;
import com.flowledger.organization.dto.*;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.entity.OrganizationSettings;
import com.flowledger.organization.mapper.OrganizationMapper;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.organization.repository.OrganizationSettingsRepository;
import com.flowledger.storage.StorageService;
import java.io.InputStream;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OrganizationService {
    private final OrganizationRepository repo;
    private final OrganizationSettingsRepository settingsRepo;
    private final OrganizationMapper mapper;
    private final StorageService storage;
    private final ChartOfAccountsBootstrapService accountingBootstrap;

    public OrganizationService(
            OrganizationRepository repo,
            OrganizationSettingsRepository settingsRepo,
            OrganizationMapper mapper,
            StorageService storage,
            ChartOfAccountsBootstrapService accountingBootstrap) {
        this.repo = repo;
        this.settingsRepo = settingsRepo;
        this.mapper = mapper;
        this.storage = storage;
        this.accountingBootstrap = accountingBootstrap;
    }

    public OrganizationResponse current() {
        return mapper.toResponse(get(SecurityUtils.currentOrganizationId()));
    }

    @Transactional
    public OrganizationResponse update(UUID id, UpdateOrganizationRequest request) {
        Organization organization = get(id);
        mapper.update(request, organization);
        validateRequiredFields(organization);
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

    @Transactional(readOnly = true)
    public OrganizationLogo currentLogo() {
        Organization organization = get(SecurityUtils.currentOrganizationId());
        String key = organization.getLogoObjectKey();
        if (key == null || key.isBlank()) {
            return null;
        }
        try (InputStream in = storage.get(key)) {
            return new OrganizationLogo(in.readAllBytes(), mimeFromKey(key));
        } catch (Exception ex) {
            return null;
        }
    }

    public record OrganizationLogo(byte[] bytes, String contentType) {}

    private static String mimeFromKey(String objectKey) {
        String lower = objectKey.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "image/png";
    }

    @Transactional
    public OrganizationResponse completeOnboarding(UUID id) {
        Organization organization = get(id);
        validateRequiredFields(organization);
        organization.setOnboardingCompleted(true);
        organization.setOnboardingCompletedAt(Instant.now());
        Organization saved = repo.save(organization);
        accountingBootstrap.bootstrapOrganization(saved.getId(), saved.getFinancialYearStart());
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public OrganizationSettingsResponse settings() {
        UUID orgId = SecurityUtils.currentOrganizationId();
        OrganizationSettings settings = settingsRepo
                .findByOrganizationId(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization settings not found"));
        return mapper.toResponse(settings);
    }

    @Transactional
    public OrganizationSettingsResponse updateSettings(UpdateOrganizationSettingsRequest request) {
        UUID orgId = SecurityUtils.currentOrganizationId();
        OrganizationSettings settings = settingsRepo
                .findByOrganizationId(orgId)
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
        if (request.transportEnabled() != null) {
            settings.setTransportEnabled(request.transportEnabled());
        }
        if (request.retailEnabled() != null) {
            settings.setRetailEnabled(request.retailEnabled());
        }
        if (request.transportRequiredDefault() != null) {
            settings.setTransportRequiredDefault(request.transportRequiredDefault());
        }
        if (request.transportAllowOverride() != null) {
            settings.setTransportAllowOverride(request.transportAllowOverride());
        }
        if (request.transportApprovalRequired() != null) {
            settings.setTransportApprovalRequired(request.transportApprovalRequired());
        }
        if (request.transportDefaultFreightPayer() != null) {
            settings.setTransportDefaultFreightPayer(request.transportDefaultFreightPayer());
        }
        if (request.transportDelayThresholdHours() != null) {
            settings.setTransportDelayThresholdHours(request.transportDelayThresholdHours());
        }
        if (request.settingsJson() != null) {
            settings.setSettingsJson(request.settingsJson());
        }
        return mapper.toResponse(settingsRepo.save(settings));
    }

    private void validateRequiredFields(Organization organization) {
        Map<String, String> errors = new LinkedHashMap<>();
        checkText(errors, "name", organization.getName(), "Organization name is required");
        checkText(errors, "country", organization.getCountry(), "Country is required");
        checkText(errors, "currency", organization.getCurrency(), "Currency is required");
        checkText(
                errors, "financialYearStart", organization.getFinancialYearStart(), "Financial year start is required");
        checkText(errors, "invoicePrefix", organization.getInvoicePrefix(), "Invoice prefix is required");
        checkText(
                errors,
                "invoiceNumberFormat",
                organization.getInvoiceNumberFormat(),
                "Invoice number format is required");
        if (!errors.isEmpty()) {
            throw new com.flowledger.common.exception.ValidationException(errors);
        }
    }

    private void checkText(Map<String, String> errors, String field, String value, String message) {
        if (value == null || value.isBlank()) {
            errors.put(field, message);
        }
    }

    private Organization get(UUID id) {
        return repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
    }
}
