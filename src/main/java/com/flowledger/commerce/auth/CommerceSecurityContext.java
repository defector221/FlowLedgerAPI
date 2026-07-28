package com.flowledger.commerce.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class CommerceSecurityContext {
    private CommerceSecurityContext() {}

    public static CommercePrincipal currentCustomer() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof CommercePrincipal principal) {
            return principal;
        }
        throw new IllegalStateException("No commerce customer in security context");
    }
}
