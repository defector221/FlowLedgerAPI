package com.flowledger.auth.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public final class UserDtos {
    private UserDtos() {}

    public record InviteUserRequest(
            @NotBlank String firstName,
            String lastName,
            @Email @NotBlank String email,
            @NotBlank String role,
            String phone) {}

    public record UpdateUserRequest(@NotBlank String firstName, String lastName, String phone) {}

    public record ChangeRoleRequest(@NotBlank String role) {}

    public record UserListResponse(
            UUID id,
            String email,
            String firstName,
            String lastName,
            String phone,
            String status,
            Set<String> roles,
            Instant lastLoginAt) {}

    public record InvitationPreviewResponse(String organizationName, String email, String firstName, String lastName) {}

    public record AcceptInvitationRequest(@NotBlank String token, @NotBlank @Size(min = 8) String password) {}
}
