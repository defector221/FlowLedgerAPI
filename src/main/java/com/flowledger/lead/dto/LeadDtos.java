package com.flowledger.lead.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class LeadDtos {
    private LeadDtos() {}

    public record Create(
            @NotBlank String leadName,
            String companyName,
            String email,
            String phone,
            String source,
            String status,
            UUID assignedTo,
            String notes,
            BigDecimal estimatedValue) {}

    public record Update(
            @NotBlank String leadName,
            String companyName,
            String email,
            String phone,
            String source,
            String status,
            UUID assignedTo,
            String notes,
            BigDecimal estimatedValue) {}

    public record Response(
            UUID id,
            String leadName,
            String companyName,
            String email,
            String phone,
            String source,
            String status,
            UUID assignedTo,
            String notes,
            BigDecimal estimatedValue,
            UUID convertedCustomerId,
            OffsetDateTime convertedAt,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record FollowUpCreate(@NotNull OffsetDateTime followUpAt, String notes) {}

    public record FollowUpResponse(
            UUID id,
            UUID leadId,
            OffsetDateTime followUpAt,
            String notes,
            String status,
            OffsetDateTime completedAt,
            OffsetDateTime createdAt) {}

    public record ConvertRequest(UUID customerId) {}
}
