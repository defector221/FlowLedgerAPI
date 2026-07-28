package com.flowledger.commerce.common;

import com.flowledger.common.tenant.TenantContext;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

public final class CommerceTenantScope {
    private CommerceTenantScope() {}

    public static <T> T run(UUID organizationId, Supplier<T> action) {
        Objects.requireNonNull(organizationId, "organizationId");
        UUID previousOrg = TenantContext.organizationId().orElse(null);
        UUID previousUser = TenantContext.userId().orElse(null);
        try {
            TenantContext.set(organizationId, previousUser);
            return action.get();
        } finally {
            if (previousOrg != null) {
                TenantContext.set(previousOrg, previousUser);
            } else {
                TenantContext.clear();
            }
        }
    }

    public static void runVoid(UUID organizationId, Runnable action) {
        run(organizationId, () -> {
            action.run();
            return null;
        });
    }
}
