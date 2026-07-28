package com.flowledger.ops.service;

import com.flowledger.commerce.dto.CommerceDtos;
import com.flowledger.commerce.integration.entity.MerchantIntegrationProfile;
import com.flowledger.commerce.integration.repository.MerchantIntegrationProfileRepository;
import com.flowledger.commerce.mapper.CommerceMapper;
import com.flowledger.commerce.onboarding.MerchantOnboardingService;
import com.flowledger.commerce.onboarding.domain.MerchantOnboardingState;
import com.flowledger.commerce.onboarding.entity.MerchantOnboarding;
import com.flowledger.commerce.onboarding.repository.MerchantOnboardingRepository;
import com.flowledger.commerce.capability.repository.MerchantCapabilityProfileRepository;
import com.flowledger.commerce.store.repository.StoreCommerceProfileRepository;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OpsCommerceService {
    private final MerchantOnboardingRepository onboardingRepository;
    private final MerchantIntegrationProfileRepository integrationProfiles;
    private final MerchantCapabilityProfileRepository capabilityProfiles;
    private final StoreCommerceProfileRepository storeProfiles;
    private final OrganizationRepository organizations;
    private final MerchantOnboardingService onboardingService;
    private final CommerceMapper mapper;

    public OpsCommerceService(
            MerchantOnboardingRepository onboardingRepository,
            MerchantIntegrationProfileRepository integrationProfiles,
            MerchantCapabilityProfileRepository capabilityProfiles,
            StoreCommerceProfileRepository storeProfiles,
            OrganizationRepository organizations,
            MerchantOnboardingService onboardingService,
            CommerceMapper mapper) {
        this.onboardingRepository = onboardingRepository;
        this.integrationProfiles = integrationProfiles;
        this.capabilityProfiles = capabilityProfiles;
        this.storeProfiles = storeProfiles;
        this.organizations = organizations;
        this.onboardingService = onboardingService;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Page<CommerceDtos.OpsMerchantRow> listMerchants(Pageable pageable) {
        return onboardingRepository.findAll(pageable).map(onboarding -> {
            Organization org = organizations.findById(onboarding.getOrganizationId()).orElse(null);
            MerchantIntegrationProfile integration = integrationProfiles
                    .findByOrganizationId(onboarding.getOrganizationId())
                    .orElse(null);
            return new CommerceDtos.OpsMerchantRow(
                    onboarding.getOrganizationId(),
                    org != null ? org.getName() : "Unknown",
                    onboarding.getState(),
                    integration != null ? integration.getMerchantType() : null,
                    integration != null ? integration.getHealthStatus() : "UNKNOWN",
                    integration != null ? integration.getLastSyncAt() : null);
        });
    }

    @Transactional(readOnly = true)
    public CommerceDtos.OpsMerchantDetailResponse getMerchant(UUID organizationId) {
        MerchantOnboarding onboarding = onboardingService.required(organizationId);
        Organization org = organizations.findById(organizationId).orElse(null);
        MerchantIntegrationProfile integration = integrationProfiles
                .findByOrganizationId(organizationId)
                .orElse(null);
        var capabilities = capabilityProfiles.findByOrganizationId(organizationId).orElse(null);
        long enabledStores = storeProfiles.countByOrganizationIdAndCommerceEnabledTrue(organizationId);
        long publishedStores = storeProfiles.findByOrganizationId(organizationId).stream()
                .filter(p -> p.isPublishedToMarketplace())
                .count();
        return new CommerceDtos.OpsMerchantDetailResponse(
                organizationId,
                org != null ? org.getName() : "Unknown",
                onboarding.getState(),
                integration != null ? mapper.toIntegrationResponse(integration) : null,
                capabilities != null ? mapper.toCapabilityResponse(capabilities) : null,
                enabledStores,
                publishedStores);
    }

    public MerchantOnboarding onboard(UUID organizationId, CommerceDtos.OpsOnboardMerchantRequest request, UUID actorId) {
        return onboardingService.onboard(organizationId, request, actorId);
    }

    public MerchantOnboarding transition(UUID organizationId, MerchantOnboardingState state, UUID actorId) {
        return onboardingService.transition(organizationId, state, actorId);
    }

    public MerchantOnboarding suspend(UUID organizationId, UUID actorId) {
        return onboardingService.suspend(organizationId, actorId);
    }

    public MerchantOnboarding activate(UUID organizationId, UUID actorId) {
        return onboardingService.activate(organizationId, actorId);
    }

    @Transactional(readOnly = true)
    public List<CommerceDtos.IntegrationHealthRow> integrationHealth() {
        List<CommerceDtos.IntegrationHealthRow> rows = new ArrayList<>();
        for (MerchantIntegrationProfile profile : integrationProfiles.findAll()) {
            Organization org = organizations.findById(profile.getOrganizationId()).orElse(null);
            rows.add(new CommerceDtos.IntegrationHealthRow(
                    profile.getOrganizationId(),
                    org != null ? org.getName() : "Unknown",
                    profile.getMerchantType(),
                    profile.getConnectorType(),
                    profile.getHealthStatus(),
                    profile.getLastSyncAt(),
                    profile.getLastHealthCheckAt()));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public CommerceDtos.CommerceDashboardSummary dashboardSummary() {
        return new CommerceDtos.CommerceDashboardSummary(
                onboardingRepository.count(),
                onboardingRepository.countByState(MerchantOnboardingState.LIVE),
                onboardingRepository.countByState(MerchantOnboardingState.SUSPENDED),
                integrationProfiles.findAll().stream()
                        .filter(p -> !"HEALTHY".equals(p.getHealthStatus()))
                        .count());
    }
}
