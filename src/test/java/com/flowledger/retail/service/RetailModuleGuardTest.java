package com.flowledger.retail.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.platform.domain.ModuleCodes;
import com.flowledger.platform.service.FeatureService;
import com.flowledger.platform.service.ModuleEntitlementCache;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class RetailModuleGuardTest {
    UUID orgId = UUID.randomUUID();
    AtomicBoolean retailEnabled = new AtomicBoolean(false);
    RetailModuleGuard guard;

    @BeforeEach
    void setUp() {
        ModuleEntitlementCache cache = new ModuleEntitlementCache(null, null) {
            @Override
            public Snapshot get(UUID organizationId) {
                return new Snapshot(Map.of(ModuleCodes.RETAIL, retailEnabled.get()), Map.of());
            }
        };
        guard = new RetailModuleGuard(new FeatureService(cache));
        TenantContext.set(orgId, UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void rejectsWhenRetailDisabled() {
        retailEnabled.set(false);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> guard.ensureEnabled());
        assertEquals(403, ex.getStatusCode().value());
    }

    @Test
    void allowsWhenRetailEnabled() {
        retailEnabled.set(true);
        assertEquals(orgId, guard.ensureEnabled());
    }
}
