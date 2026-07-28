package com.flowledger.commerce.onboarding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowledger.commerce.capability.repository.MerchantCapabilityProfileRepository;
import com.flowledger.commerce.dto.CommerceDtos.OpsOnboardMerchantRequest;
import com.flowledger.commerce.integration.domain.ConnectorType;
import com.flowledger.commerce.integration.domain.IntegrationType;
import com.flowledger.commerce.integration.repository.MerchantIntegrationProfileRepository;
import com.flowledger.commerce.merchant.domain.MerchantType;
import com.flowledger.commerce.onboarding.domain.MerchantOnboardingState;
import com.flowledger.commerce.onboarding.entity.MerchantOnboarding;
import com.flowledger.commerce.onboarding.repository.MerchantOnboardingRepository;
import com.flowledger.commerce.store.repository.StoreCommerceProfileRepository;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.platform.domain.ModuleCodes;
import com.flowledger.platform.event.DomainEventPublisher;
import com.flowledger.platform.service.FeatureService;
import com.flowledger.platform.service.OrganizationModuleService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerchantOnboardingServiceTest {
    @Mock
    private MerchantOnboardingRepository onboardingRepository;
    @Mock
    private MerchantIntegrationProfileRepository integrationProfiles;
    @Mock
    private MerchantCapabilityProfileRepository capabilityProfiles;
    @Mock
    private StoreCommerceProfileRepository storeProfiles;
    @Mock
    private OrganizationRepository organizations;
    @Mock
    private OrganizationModuleService moduleService;
    @Mock
    private FeatureService featureService;
    @Mock
    private DomainEventPublisher events;

    private MerchantOnboardingService service;
    private UUID orgId;

    @BeforeEach
    void setUp() {
        service = new MerchantOnboardingService(
                onboardingRepository,
                integrationProfiles,
                capabilityProfiles,
                storeProfiles,
                organizations,
                moduleService,
                featureService,
                events);
        orgId = UUID.randomUUID();
    }

    @Test
    void onboardPartnerSetsConnectorDefaults() {
        when(organizations.findById(orgId)).thenReturn(Optional.of(new Organization()));
        when(onboardingRepository.findByOrganizationId(orgId)).thenReturn(Optional.empty());

        OpsOnboardMerchantRequest request = new OpsOnboardMerchantRequest(MerchantType.PARTNER, null, null);
        MerchantOnboarding onboarding = service.onboard(orgId, request, UUID.randomUUID());

        assertEquals(MerchantOnboardingState.REGISTERED, onboarding.getState());
        ArgumentCaptor<com.flowledger.commerce.integration.entity.MerchantIntegrationProfile> captor =
                ArgumentCaptor.forClass(com.flowledger.commerce.integration.entity.MerchantIntegrationProfile.class);
        verify(integrationProfiles).save(captor.capture());
        assertEquals(MerchantType.PARTNER, captor.getValue().getMerchantType());
        assertEquals(ConnectorType.CUSTOM, captor.getValue().getConnectorType());
        assertEquals(IntegrationType.API, captor.getValue().getIntegrationType());
        verify(moduleService).setModuleEnabled(eq(orgId), eq(ModuleCodes.COMMERCE), eq(true), any());
    }

    @Test
    void rejectsDuplicateOnboard() {
        when(organizations.findById(orgId)).thenReturn(Optional.of(new Organization()));
        when(onboardingRepository.findByOrganizationId(orgId)).thenReturn(Optional.of(new MerchantOnboarding()));

        assertThrows(
                BusinessException.class,
                () -> service.onboard(orgId, new OpsOnboardMerchantRequest(MerchantType.FLOWLEDGER, null, null), null));
    }
}
