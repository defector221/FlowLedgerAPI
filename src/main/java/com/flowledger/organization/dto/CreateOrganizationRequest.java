package com.flowledger.organization.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateOrganizationRequest(@NotBlank String name, String legalName, String email, String phone) {}
