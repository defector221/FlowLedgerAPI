package com.flowledger.marketing.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class CampaignDtos {
    private CampaignDtos() {}

    public record AudienceFilter(String leadStatus, String search, Boolean includeArchivedCustomers) {}

    public record UpsertCampaignRequest(
            @NotBlank String name,
            @NotBlank String audienceType,
            JsonNode filterJson,
            @NotNull UUID emailTemplateId,
            List<UUID> leadIds,
            List<UUID> customerIds) {}

    public record ScheduleRequest(OffsetDateTime scheduledAt) {}

    public record CampaignResponse(
            UUID id,
            String name,
            String status,
            String audienceType,
            JsonNode filterJson,
            UUID emailTemplateId,
            OffsetDateTime scheduledAt,
            OffsetDateTime startedAt,
            OffsetDateTime completedAt,
            int totalCount,
            int sentCount,
            int failedCount,
            int skippedCount,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record RecipientResponse(
            UUID id,
            String recipientType,
            UUID recipientId,
            String email,
            String status,
            String errorMessage,
            OffsetDateTime sentAt) {}

    public record AudiencePreviewResponse(int count, int cappedAt) {}
}
