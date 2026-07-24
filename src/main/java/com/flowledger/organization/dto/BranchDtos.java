package com.flowledger.organization.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public final class BranchDtos {
    private BranchDtos() {}

    public record BranchRequest(
            @NotBlank String code,
            @NotBlank String name,
            String addressLine1,
            String city,
            String state,
            String postalCode,
            String country,
            Boolean active,
            Boolean defaultBranch) {}

    public record BranchResponse(
            UUID id,
            String code,
            String name,
            String addressLine1,
            String city,
            String state,
            String postalCode,
            String country,
            boolean active,
            boolean defaultBranch) {}
}
