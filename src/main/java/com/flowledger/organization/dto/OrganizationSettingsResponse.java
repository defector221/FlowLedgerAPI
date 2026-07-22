package com.flowledger.organization.dto;

import java.util.UUID;

public record OrganizationSettingsResponse(
        UUID id,
        UUID organizationId,
        String inventoryDeductionEvent,
        boolean taxInclusiveDefault,
        boolean roundOffEnabled,
        UUID defaultWarehouseId,
        boolean transportEnabled,
        boolean transportRequiredDefault,
        boolean transportAllowOverride,
        boolean transportApprovalRequired,
        String transportDefaultFreightPayer,
        Integer transportDelayThresholdHours,
        String settingsJson) {}
