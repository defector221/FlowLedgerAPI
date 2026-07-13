package com.flowledger.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record ChangePasswordRequest(
        @NotBlank String currentPassword, @NotBlank @jakarta.validation.constraints.Size(min = 8) String newPassword) {}
