package com.flowledger.commerce.common;

import com.flowledger.common.tenant.TenantContext;
import java.util.UUID;
import java.util.function.Supplier;

public final class CommerceTenantScope {
    private CommerceTenantScope() {}

    public static <T> T run(UUID organizationId, Supplier<T> action) {
        try {
            TenantContext.set(organizationId, null);
            return action.get();
        } finally {
            TenantContext.clear();
        }
    }

    public static void runVoid(UUID organizationId, Runnable action) {
        run(organizationId, () -> {
            action.run();
            return null;
        });
    }
}
