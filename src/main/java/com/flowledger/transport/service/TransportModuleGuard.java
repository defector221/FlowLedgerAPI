package com.flowledger.transport.service;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.platform.domain.ModuleCodes;
import com.flowledger.platform.service.FeatureService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Ensures the transport module is enabled for the current tenant.
 */
@Component
public class TransportModuleGuard {
    private final FeatureService featureService;

    public TransportModuleGuard(FeatureService featureService) {
        this.featureService = featureService;
    }

    public UUID ensureEnabled() {
        UUID organizationId = TenantContext.getOrganizationId();
        if (!featureService.hasModule(organizationId, ModuleCodes.TRANSPORT)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Transport module is not enabled for this organization");
        }
        return organizationId;
    }
}
