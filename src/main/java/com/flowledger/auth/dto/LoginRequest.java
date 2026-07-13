package com.flowledger.auth.dto;

import jakarta.validation.constraints.*;

public record LoginRequest(@NotNull java.util.UUID organizationId, @Email @NotBlank String email,
                           @NotBlank String password) {
}
