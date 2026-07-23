package com.flowledger.platform.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.platform.repository.OrganizationFeatureRepository;
import com.flowledger.platform.repository.OrganizationModuleRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FeatureServiceTest {
    UUID orgId = UUID.randomUUID();
    FeatureService featureService;

    @BeforeEach
    void setUp() {
        ModuleEntitlementCache cache = new ModuleEntitlementCache(
                (OrganizationModuleRepository) null, (OrganizationFeatureRepository) null) {
            @Override
            public Snapshot get(UUID organizationId) {
                return new Snapshot(
                        Map.of("RETAIL", true, "TRANSPORT", false, "SALES", true),
                        Map.of("RETAIL.POS", true, "RETAIL.LOYALTY", false));
            }
        };
        featureService = new FeatureService(cache);
        TenantContext.set(orgId, UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void hasModuleRespectsEntitlements() {
        assertTrue(featureService.hasModule("RETAIL"));
        assertFalse(featureService.hasModule("TRANSPORT"));
        assertTrue(featureService.hasModule("SALES"));
    }

    @Test
    void hasFeatureRequiresModuleAndFeature() {
        assertTrue(featureService.hasFeature("RETAIL", "POS"));
        assertFalse(featureService.hasFeature("RETAIL", "LOYALTY"));
        assertFalse(featureService.hasFeature("TRANSPORT", "TRACKING"));
    }

    @Test
    void missingFeatureOverrideDefaultsToEnabledWhenModuleOn() {
        assertTrue(featureService.hasFeature("RETAIL", "STORES"));
    }
}
