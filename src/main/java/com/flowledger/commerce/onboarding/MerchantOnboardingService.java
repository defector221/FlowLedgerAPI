package com.flowledger.commerce.onboarding;

import com.flowledger.commerce.capability.entity.MerchantCapabilityProfile;
import com.flowledger.commerce.capability.repository.MerchantCapabilityProfileRepository;
import com.flowledger.commerce.dto.CommerceDtos.OpsOnboardMerchantRequest;
import com.flowledger.commerce.events.MerchantActivatedEvent;
import com.flowledger.commerce.events.MerchantRegisteredEvent;
import com.flowledger.commerce.integration.domain.ConnectorType;
import com.flowledger.commerce.integration.domain.IntegrationType;
import com.flowledger.commerce.integration.entity.MerchantIntegrationProfile;
import com.flowledger.commerce.integration.repository.MerchantIntegrationProfileRepository;
import com.flowledger.commerce.merchant.domain.MerchantType;
import com.flowledger.commerce.onboarding.domain.MerchantOnboardingState;
import com.flowledger.commerce.onboarding.entity.MerchantOnboarding;
import com.flowledger.commerce.onboarding.repository.MerchantOnboardingRepository;
import com.flowledger.commerce.store.repository.StoreCommerceProfileRepository;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.platform.domain.ModuleCodes;
import com.flowledger.platform.event.DomainEventPublisher;
import com.flowledger.platform.service.OrganizationModuleService;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class MerchantOnboardingService {
    private final MerchantOnboardingRepository onboardingRepository;
    private final MerchantIntegrationProfileRepository integrationProfiles;
    private final MerchantCapabilityProfileRepository capabilityProfiles;
    private final StoreCommerceProfileRepository storeProfiles;
    private final OrganizationRepository organizations;
    private final OrganizationModuleService moduleService;
    private final DomainEventPublisher events;

    public MerchantOnboardingService(
            MerchantOnboardingRepository onboardingRepository,
            MerchantIntegrationProfileRepository integrationProfiles,
            MerchantCapabilityProfileRepository capabilityProfiles,
            StoreCommerceProfileRepository storeProfiles,
            OrganizationRepository organizations,
            OrganizationModuleService moduleService,
            DomainEventPublisher events) {
        this.onboardingRepository = onboardingRepository;
        this.integrationProfiles = integrationProfiles;
        this.capabilityProfiles = capabilityProfiles;
        this.storeProfiles = storeProfiles;
        this.organizations = organizations;
        this.moduleService = moduleService;
        this.events = events;
    }

    public MerchantOnboarding onboard(UUID organizationId, OpsOnboardMerchantRequest request, UUID actorId) {
        organizations.findById(organizationId).orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
        if (onboardingRepository.findByOrganizationId(organizationId).isPresent()) {
            throw new BusinessException("Merchant already onboarded for organization");
        }

        MerchantType merchantType = request.merchantType();
        MerchantIntegrationProfile integration = new MerchantIntegrationProfile();
        integration.setOrganizationId(organizationId);
        integration.setMerchantType(merchantType);
        integration.setConnectorType(
                request.connectorType() != null
                        ? request.connectorType()
                        : merchantType == MerchantType.FLOWLEDGER ? ConnectorType.FLOWLEDGER : ConnectorType.CUSTOM);
        integration.setIntegrationType(
                request.integrationType() != null
                        ? request.integrationType()
                        : merchantType == MerchantType.FLOWLEDGER ? IntegrationType.FLOWLEDGER : IntegrationType.API);
        integration.setStatus("ACTIVE");
        integration.setHealthStatus("HEALTHY");
        integrationProfiles.save(integration);

        MerchantCapabilityProfile capabilities = new MerchantCapabilityProfile();
        capabilities.setOrganizationId(organizationId);
        capabilities.setSupportsMarketplace(true);
        capabilityProfiles.save(capabilities);

        MerchantOnboarding onboarding = new MerchantOnboarding();
        onboarding.setOrganizationId(organizationId);
        onboarding.setState(MerchantOnboardingState.REGISTERED);
        onboardingRepository.save(onboarding);

        moduleService.setModuleEnabled(organizationId, ModuleCodes.COMMERCE, true, actorId);
        events.publish(new MerchantRegisteredEvent(this, organizationId, actorId));
        return onboarding;
    }

    public MerchantOnboarding transition(UUID organizationId, MerchantOnboardingState state, UUID actorId) {
        MerchantOnboarding onboarding = required(organizationId);
        onboarding.advanceTo(state);
        onboardingRepository.save(onboarding);
        maybeAutoAdvance(organizationId, onboarding);
        if (onboarding.getState() == MerchantOnboardingState.LIVE) {
            events.publish(new MerchantActivatedEvent(this, organizationId, actorId));
        }
        return onboarding;
    }

    public void maybeAutoAdvance(UUID organizationId, MerchantOnboarding onboarding) {
        if (onboarding.getState() == MerchantOnboardingState.REGISTERED
                || onboarding.getState() == MerchantOnboardingState.EMAIL_VERIFIED
                || onboarding.getState() == MerchantOnboardingState.BUSINESS_VERIFIED) {
            if (storeProfiles.countByOrganizationIdAndCommerceEnabledTrue(organizationId) > 0) {
                onboarding.advanceTo(MerchantOnboardingState.STORE_CONFIGURED);
            }
        }
        if (onboarding.getState() == MerchantOnboardingState.STORE_CONFIGURED) {
            integrationProfiles.findByOrganizationId(organizationId).ifPresent(i -> {
                if ("ACTIVE".equals(i.getStatus())) {
                    onboarding.advanceTo(MerchantOnboardingState.COMMERCE_ENABLED);
                }
            });
        }
        if (onboarding.getState() == MerchantOnboardingState.COMMERCE_ENABLED) {
            capabilityProfiles.findByOrganizationId(organizationId).ifPresent(c -> {
                if (c.isSupportsMarketplace()) {
                    onboarding.advanceTo(MerchantOnboardingState.READY);
                }
            });
        }
        onboardingRepository.save(onboarding);
    }

    public MerchantOnboarding suspend(UUID organizationId, UUID actorId) {
        MerchantOnboarding onboarding = required(organizationId);
        onboarding.advanceTo(MerchantOnboardingState.SUSPENDED);
        return onboardingRepository.save(onboarding);
    }

    public MerchantOnboarding activate(UUID organizationId, UUID actorId) {
        MerchantOnboarding onboarding = required(organizationId);
        onboarding.advanceTo(MerchantOnboardingState.LIVE);
        onboardingRepository.save(onboarding);
        events.publish(new MerchantActivatedEvent(this, organizationId, actorId));
        return onboarding;
    }

    public MerchantOnboarding required(UUID organizationId) {
        return onboardingRepository
                .findByOrganizationId(organizationId)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant onboarding not found"));
    }
}
