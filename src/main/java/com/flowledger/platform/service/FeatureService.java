package com.flowledger.platform.service;

import com.flowledger.common.tenant.TenantContext;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class FeatureService {
    private final ModuleEntitlementCache cache;

    public FeatureService(ModuleEntitlementCache cache) {
        this.cache = cache;
    }

    public boolean hasModule(String moduleCode) {
        UUID orgId = TenantContext.getOrganizationId();
        if (orgId == null || moduleCode == null) {
            return false;
        }
        return Boolean.TRUE.equals(cache.get(orgId).modules().get(moduleCode));
    }

    public boolean hasModule(UUID organizationId, String moduleCode) {
        if (organizationId == null || moduleCode == null) {
            return false;
        }
        return Boolean.TRUE.equals(cache.get(organizationId).modules().get(moduleCode));
    }

    public boolean hasFeature(String moduleCode, String featureCode) {
        UUID orgId = TenantContext.getOrganizationId();
        if (orgId == null || moduleCode == null || featureCode == null) {
            return false;
        }
        if (!hasModule(orgId, moduleCode)) {
            return false;
        }
        String key = moduleCode + "." + featureCode;
        Boolean override = cache.get(orgId).features().get(key);
        // No override row ⇒ feature follows module enablement (catalog default)
        return override == null || Boolean.TRUE.equals(override);
    }

    public boolean hasFeature(UUID organizationId, String moduleCode, String featureCode) {
        if (organizationId == null || moduleCode == null || featureCode == null) {
            return false;
        }
        if (!hasModule(organizationId, moduleCode)) {
            return false;
        }
        String key = moduleCode + "." + featureCode;
        Boolean override = cache.get(organizationId).features().get(key);
        return override == null || Boolean.TRUE.equals(override);
    }

    /**
     * Module + optional feature + Spring Security authority.
     * When {@code featureCode} is null, only module + authority are checked.
     */
    public boolean canAccess(String moduleCode, String featureCode, String permissionAuthority) {
        if (!hasModule(moduleCode)) {
            return false;
        }
        if (featureCode != null && !featureCode.isBlank() && !hasFeature(moduleCode, featureCode)) {
            return false;
        }
        if (permissionAuthority == null || permissionAuthority.isBlank()) {
            return true;
        }
        return hasAuthority(permissionAuthority);
    }

    private static boolean hasAuthority(String permissionAuthority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        String role = "ROLE_" + permissionAuthority;
        for (GrantedAuthority granted : auth.getAuthorities()) {
            String a = granted.getAuthority();
            if (permissionAuthority.equals(a) || role.equals(a) || "ROLE_ORGANIZATION_ADMIN".equals(a)) {
                return true;
            }
        }
        return false;
    }
}
