package com.flowledger.common.tenant;

import java.util.Optional;
import java.util.UUID;

public final class TenantContext {
    private static final ThreadLocal<UUID> ORGANIZATION = new ThreadLocal<>();
    private static final ThreadLocal<UUID> USER = new ThreadLocal<>();
    private static final ThreadLocal<UUID> BRANCH = new ThreadLocal<>();
    private static final ThreadLocal<UUID> STORE = new ThreadLocal<>();
    private static final ThreadLocal<UUID> WAREHOUSE = new ThreadLocal<>();
    private static final ThreadLocal<UUID> IAM_USER = new ThreadLocal<>();
    private static final ThreadLocal<UUID> IAM_ORG = new ThreadLocal<>();
    private static final ThreadLocal<UUID> IAM_MEMBERSHIP = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> IMMUTABLE = new ThreadLocal<>();

    private TenantContext() {}

    public static UUID getOrganizationId() {
        return organizationId().orElseThrow(() -> new IllegalStateException("Organization context is not set"));
    }

    public static Optional<UUID> organizationId() {
        return Optional.ofNullable(ORGANIZATION.get());
    }

    public static void setOrganizationId(UUID id) {
        assertMutable();
        ORGANIZATION.set(id);
    }

    public static void set(UUID organizationId, UUID userId) {
        assertMutable();
        ORGANIZATION.set(organizationId);
        USER.set(userId);
    }

    /**
     * Bind local + IAM identity once at the security boundary. Subsequent set, setOrganizationId, and setUserId
     * calls throw while IMMUTABLE is true.
     */
    public static void bindImmutable(
            UUID localOrgId, UUID localUserId, UUID iamUserId, UUID iamOrgId, UUID iamMembershipId) {
        ORGANIZATION.set(localOrgId);
        USER.set(localUserId);
        IAM_USER.set(iamUserId);
        IAM_ORG.set(iamOrgId);
        IAM_MEMBERSHIP.set(iamMembershipId);
        IMMUTABLE.set(Boolean.TRUE);
    }

    public static void setLocation(UUID branchId, UUID storeId, UUID warehouseId) {
        BRANCH.set(branchId);
        STORE.set(storeId);
        WAREHOUSE.set(warehouseId);
    }

    public static Optional<UUID> userId() {
        return Optional.ofNullable(USER.get());
    }

    public static Optional<UUID> iamUserId() {
        return Optional.ofNullable(IAM_USER.get());
    }

    public static Optional<UUID> iamOrganizationId() {
        return Optional.ofNullable(IAM_ORG.get());
    }

    public static Optional<UUID> iamMembershipId() {
        return Optional.ofNullable(IAM_MEMBERSHIP.get());
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
        assertMutable();
        USER.set(id);
    }

    public static void clear() {
        ORGANIZATION.remove();
        USER.remove();
        BRANCH.remove();
        STORE.remove();
        WAREHOUSE.remove();
        IAM_USER.remove();
        IAM_ORG.remove();
        IAM_MEMBERSHIP.remove();
        IMMUTABLE.remove();
    }

    private static void assertMutable() {
        if (Boolean.TRUE.equals(IMMUTABLE.get())) {
            throw new IllegalStateException("TenantContext is immutable for this request");
        }
    }
}
