package com.flowledger.common.tenant;

import java.util.Optional;
import java.util.UUID;

public final class TenantContext {
    private static final ThreadLocal<UUID> ORGANIZATION = new ThreadLocal<>();
    private static final ThreadLocal<UUID> USER = new ThreadLocal<>();

    private TenantContext() {}

    public static UUID getOrganizationId() {
        return organizationId().orElseThrow(() -> new IllegalStateException("Organization context is not set"));
    }

    public static Optional<UUID> organizationId() {
        return Optional.ofNullable(ORGANIZATION.get());
    }

    public static void setOrganizationId(UUID id) {
        ORGANIZATION.set(id);
    }

    public static void set(UUID organizationId, UUID userId) {
        ORGANIZATION.set(organizationId);
        USER.set(userId);
    }

    public static Optional<UUID> userId() {
        return Optional.ofNullable(USER.get());
    }

    public static void setUserId(UUID id) {
        USER.set(id);
    }

    public static void clear() {
        ORGANIZATION.remove();
        USER.remove();
    }
}
