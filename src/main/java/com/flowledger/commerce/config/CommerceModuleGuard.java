package com.flowledger.commerce.config;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.platform.domain.ModuleCodes;
import com.flowledger.platform.service.FeatureService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class CommerceModuleGuard {
    private final FeatureService featureService;

    public CommerceModuleGuard(FeatureService featureService) {
        this.featureService = featureService;
    }

    public UUID ensureEnabled() {
        UUID organizationId = TenantContext.getOrganizationId();
        if (!featureService.hasModule(organizationId, ModuleCodes.COMMERCE)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, "Commerce module is not enabled for this organization");
        }
        return organizationId;
    }
}
