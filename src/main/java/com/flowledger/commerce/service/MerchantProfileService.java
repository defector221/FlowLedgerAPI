package com.flowledger.commerce.service;

import com.flowledger.commerce.audit.CommerceAuditService;
import com.flowledger.commerce.capability.entity.MerchantCapabilityProfile;
import com.flowledger.commerce.capability.repository.MerchantCapabilityProfileRepository;
import com.flowledger.commerce.config.CommerceModuleGuard;
import com.flowledger.commerce.dto.CommerceDtos;
import com.flowledger.commerce.events.MerchantCapabilityChangedEvent;
import com.flowledger.commerce.events.MerchantIntegrationConfiguredEvent;
import com.flowledger.commerce.integration.entity.MerchantIntegrationProfile;
import com.flowledger.commerce.integration.repository.MerchantIntegrationProfileRepository;
import com.flowledger.commerce.mapper.CommerceMapper;
import com.flowledger.commerce.onboarding.MerchantOnboardingService;
import com.flowledger.commerce.onboarding.entity.MerchantOnboarding;
import com.flowledger.commerce.onboarding.repository.MerchantOnboardingRepository;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.platform.event.DomainEventPublisher;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MerchantProfileService {
    private final CommerceModuleGuard guard;
    private final MerchantOnboardingRepository onboardingRepository;
    private final MerchantIntegrationProfileRepository integrationProfiles;
    private final MerchantCapabilityProfileRepository capabilityProfiles;
    private final MerchantOnboardingService onboardingService;
    private final CommerceAuditService audit;
    private final CommerceMapper mapper;
    private final DomainEventPublisher events;

    public MerchantProfileService(
            CommerceModuleGuard guard,
            MerchantOnboardingRepository onboardingRepository,
            MerchantIntegrationProfileRepository integrationProfiles,
            MerchantCapabilityProfileRepository capabilityProfiles,
            MerchantOnboardingService onboardingService,
            CommerceAuditService audit,
            CommerceMapper mapper,
            DomainEventPublisher events) {
        this.guard = guard;
        this.onboardingRepository = onboardingRepository;
        this.integrationProfiles = integrationProfiles;
        this.capabilityProfiles = capabilityProfiles;
        this.onboardingService = onboardingService;
        this.audit = audit;
        this.mapper = mapper;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public CommerceDtos.MerchantProfileResponse getProfile() {
        UUID orgId = guard.ensureEnabled();
        MerchantOnboarding onboarding = onboardingRepository
                .findByOrganizationId(orgId)
                .orElseGet(() -> {
                    MerchantOnboarding created = new MerchantOnboarding();
                    created.setOrganizationId(orgId);
                    return onboardingRepository.save(created);
                });
        MerchantIntegrationProfile integration = integrationProfiles
                .findByOrganizationId(orgId)
                .orElseGet(() -> {
                    MerchantIntegrationProfile created = new MerchantIntegrationProfile();
                    created.setOrganizationId(orgId);
                    return integrationProfiles.save(created);
                });
        MerchantCapabilityProfile capabilities = capabilityProfiles
                .findByOrganizationId(orgId)
                .orElseGet(() -> {
                    MerchantCapabilityProfile created = new MerchantCapabilityProfile();
                    created.setOrganizationId(orgId);
                    return capabilityProfiles.save(created);
                });
        return new CommerceDtos.MerchantProfileResponse(
                orgId,
                onboarding.getState(),
                mapper.toIntegrationResponse(integration),
                mapper.toCapabilityResponse(capabilities));
    }

    public CommerceDtos.IntegrationProfileResponse updateIntegration(CommerceDtos.UpdateIntegrationRequest request) {
        UUID orgId = guard.ensureEnabled();
        UUID actorId = TenantContext.userId().orElse(null);
        MerchantIntegrationProfile profile = integrationProfiles
                .findByOrganizationId(orgId)
                .orElseGet(() -> {
                    MerchantIntegrationProfile created = new MerchantIntegrationProfile();
                    created.setOrganizationId(orgId);
                    return created;
                });
        if (request.integrationType() != null) profile.setIntegrationType(request.integrationType());
        if (request.connectorType() != null) profile.setConnectorType(request.connectorType());
        if (request.status() != null) profile.setStatus(request.status());
        if (request.configurationJson() != null) profile.setConfigurationJson(request.configurationJson());
        integrationProfiles.save(profile);
        onboardingService.maybeAutoAdvance(orgId, onboardingService.required(orgId));
        audit.log(orgId, "MerchantIntegrationProfile", profile.getId(), "UPDATE", null, request.configurationJson());
        events.publish(new MerchantIntegrationConfiguredEvent(this, orgId, actorId, profile.getId()));
        return mapper.toIntegrationResponse(profile);
    }

    public CommerceDtos.CapabilityProfileResponse updateCapabilities(CommerceDtos.UpdateCapabilitiesRequest request) {
        UUID orgId = guard.ensureEnabled();
        UUID actorId = TenantContext.userId().orElse(null);
        MerchantCapabilityProfile profile = capabilityProfiles
                .findByOrganizationId(orgId)
                .orElseGet(() -> {
                    MerchantCapabilityProfile created = new MerchantCapabilityProfile();
                    created.setOrganizationId(orgId);
                    return created;
                });
        if (request.supportsMarketplace() != null) profile.setSupportsMarketplace(request.supportsMarketplace());
        if (request.supportsDelivery() != null) profile.setSupportsDelivery(request.supportsDelivery());
        if (request.supportsPickup() != null) profile.setSupportsPickup(request.supportsPickup());
        if (request.supportsClickCollect() != null) profile.setSupportsClickCollect(request.supportsClickCollect());
        if (request.supportsScanAndGo() != null) profile.setSupportsScanAndGo(request.supportsScanAndGo());
        if (request.supportsScheduledDelivery() != null)
            profile.setSupportsScheduledDelivery(request.supportsScheduledDelivery());
        if (request.supportsScheduledPickup() != null) profile.setSupportsScheduledPickup(request.supportsScheduledPickup());
        if (request.supportsWallet() != null) profile.setSupportsWallet(request.supportsWallet());
        if (request.supportsCoupons() != null) profile.setSupportsCoupons(request.supportsCoupons());
        if (request.supportsLoyalty() != null) profile.setSupportsLoyalty(request.supportsLoyalty());
        if (request.supportsRecommendations() != null)
            profile.setSupportsRecommendations(request.supportsRecommendations());
        if (request.supportsReviews() != null) profile.setSupportsReviews(request.supportsReviews());
        if (request.supportsReturns() != null) profile.setSupportsReturns(request.supportsReturns());
        if (request.supportsGiftCards() != null) profile.setSupportsGiftCards(request.supportsGiftCards());
        capabilityProfiles.save(profile);
        onboardingService.maybeAutoAdvance(orgId, onboardingService.required(orgId));
        audit.log(orgId, "MerchantCapabilityProfile", profile.getId(), "UPDATE", null, null);
        events.publish(new MerchantCapabilityChangedEvent(this, orgId, actorId, profile.getId()));
        return mapper.toCapabilityResponse(profile);
    }
}
