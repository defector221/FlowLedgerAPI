package com.flowledger.marketing.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class MarketingDtos {
    private MarketingDtos() {}

    public record StepCreate(
            @Min(0) int delayDays,
            @NotBlank String channel,
            String subject,
            @NotBlank String body) {}

    public record CreateSequence(
            @NotBlank String name,
            String description,
            String status,
            @NotBlank String triggerType,
            @NotEmpty @Valid List<StepCreate> steps) {}

    public record StepResponse(
            UUID id, int stepOrder, int delayDays, String channel, String subjectTemplate, String bodyTemplate) {}

    public record SequenceResponse(
            UUID id,
            String name,
            String description,
            String status,
            String triggerType,
            List<StepResponse> steps,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record EnrollmentResponse(
            UUID id,
            UUID sequenceId,
            String recipientType,
            UUID recipientId,
            String email,
            String phone,
            String status,
            int currentStep,
            OffsetDateTime enrolledAt,
            OffsetDateTime completedAt) {}
}
