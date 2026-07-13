package com.flowledger.organization.dto;

import java.util.UUID;

public record UpdateOrganizationSettingsRequest(
        String inventoryDeductionEvent,
        Boolean taxInclusiveDefault,
        Boolean roundOffEnabled,
        UUID defaultWarehouseId,
        String settingsJson
) {}
