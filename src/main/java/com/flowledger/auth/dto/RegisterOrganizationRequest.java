package com.flowledger.auth.dto;

import jakarta.validation.constraints.*;

public record RegisterOrganizationRequest(
        @NotBlank String organizationName,
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8) String password,
        @NotBlank String firstName,
        String lastName,
        String phone) {}
