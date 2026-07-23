package com.flowledger.organization.dto;

import java.util.UUID;

public record UpdateOrganizationSettingsRequest(
        String inventoryDeductionEvent,
        Boolean taxInclusiveDefault,
        Boolean roundOffEnabled,
        UUID defaultWarehouseId,
        Boolean transportEnabled,
        Boolean retailEnabled,
        Boolean transportRequiredDefault,
        Boolean transportAllowOverride,
        Boolean transportApprovalRequired,
        String transportDefaultFreightPayer,
        Integer transportDelayThresholdHours,
        String settingsJson) {}
