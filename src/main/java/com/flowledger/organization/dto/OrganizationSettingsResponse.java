package com.flowledger.organization.dto;

import java.util.UUID;

public record OrganizationSettingsResponse(
        UUID id,
        UUID organizationId,
        String inventoryDeductionEvent,
        boolean taxInclusiveDefault,
        boolean roundOffEnabled,
        UUID defaultWarehouseId,
        String settingsJson) {}
