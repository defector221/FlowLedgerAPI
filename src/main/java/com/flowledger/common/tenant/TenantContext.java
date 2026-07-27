package com.flowledger.common.tenant;

import java.util.Optional;
import java.util.UUID;

public final class TenantContext {
    private static final ThreadLocal<UUID> ORGANIZATION = new ThreadLocal<>();
    private static final ThreadLocal<UUID> USER = new ThreadLocal<>();
    private static final ThreadLocal<UUID> BRANCH = new ThreadLocal<>();
    private static final ThreadLocal<UUID> STORE = new ThreadLocal<>();
    private static final ThreadLocal<UUID> WAREHOUSE = new ThreadLocal<>();

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

    public static void setLocation(UUID branchId, UUID storeId, UUID warehouseId) {
        BRANCH.set(branchId);
        STORE.set(storeId);
        WAREHOUSE.set(warehouseId);
    }

    public static Optional<UUID> userId() {
        return Optional.ofNullable(USER.get());
    }

    public static Optional<UUID> branchId() {
        return Optional.ofNullable(BRANCH.get());
    }

    public static Optional<UUID> getBranchId() {
        return branchId();
    }

    public static Optional<UUID> storeId() {
        return Optional.ofNullable(STORE.get());
    }

    public static Optional<UUID> getStoreId() {
        return storeId();
    }

    public static Optional<UUID> warehouseId() {
        return Optional.ofNullable(WAREHOUSE.get());
    }

    public static Optional<UUID> getWarehouseId() {
        return warehouseId();
    }

    public static void setUserId(UUID id) {
        USER.set(id);
    }

    public static void clear() {
        ORGANIZATION.remove();
        USER.remove();
        BRANCH.remove();
        STORE.remove();
        WAREHOUSE.remove();
    }
}
