package com.flowledger.emailtemplate.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public final class EmailTemplateDtos {
    private EmailTemplateDtos() {}

    public record UpsertRequest(@NotBlank String name, String subject, JsonNode designJson, String html) {}

    public record PreviewRequest(Map<String, String> mergeTags) {}

    public record Response(
            UUID id,
            String name,
            String subject,
            JsonNode designJson,
            String html,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record PreviewResponse(String subject, String html) {}
}
