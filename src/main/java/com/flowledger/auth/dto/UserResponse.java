package com.flowledger.auth.dto;

import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID id,
        UUID organizationId,
        String email,
        String firstName,
        String lastName,
        String status,
        Set<String> roles
) {}
