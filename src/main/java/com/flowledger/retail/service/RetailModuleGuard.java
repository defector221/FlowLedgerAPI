package com.flowledger.retail.service;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.platform.domain.ModuleCodes;
import com.flowledger.platform.service.FeatureService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * Ensures the retail module is enabled for the current tenant before any retail operation runs.
 */
@Component
public class RetailModuleGuard {
    private final FeatureService featureService;

    public RetailModuleGuard(FeatureService featureService) {
        this.featureService = featureService;
    }

    /**
     * @return the current organization id once retail access is confirmed.
     * @throws ResponseStatusException FORBIDDEN when retail is not enabled for the org.
     */
    public UUID ensureEnabled() {
        UUID organizationId = TenantContext.getOrganizationId();
        if (!featureService.hasModule(organizationId, ModuleCodes.RETAIL)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Retail module is not enabled for this organization");
        }
        return organizationId;
    }
}
