package com.flowledger.auth.dto;

import jakarta.validation.constraints.*;

public record ForgotPasswordRequest(@NotNull java.util.UUID organizationId, @Email @NotBlank String email) {}
