package com.flowledger.template.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public final class TemplateDtos {
    private TemplateDtos() {}

    public record TemplateRequest(
            @NotBlank String templateName, String presetKey, String documentType, JsonNode configJson) {}

    public record TemplatePreviewRequest(JsonNode configJson, String documentType, UUID sampleInvoiceId) {}

    public record Preset(String key, String name, String documentType, JsonNode config) {}
}
