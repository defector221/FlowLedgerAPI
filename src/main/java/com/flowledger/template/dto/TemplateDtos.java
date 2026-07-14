package com.flowledger.template.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public final class TemplateDtos {
    private TemplateDtos() {}

    public record TemplateRequest(
            @NotBlank String templateName,
            String presetKey,
            String documentType,
            String editorMode,
            JsonNode configJson,
            JsonNode designJson,
            String html) {}

    public record TemplatePreviewRequest(
            JsonNode configJson,
            String documentType,
            UUID sampleInvoiceId,
            String editorMode,
            String html,
            JsonNode designJson) {}

    public record Preset(String key, String name, String documentType, JsonNode config) {}
}
