package com.flowledger.auth.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SwitchOrganizationRequest(@NotNull UUID organizationId) {}
