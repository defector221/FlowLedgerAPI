package com.flowledger.common.entity;

import java.util.UUID;

public interface TenantAware {
    UUID getOrganizationId();
    void setOrganizationId(UUID organizationId);
}
