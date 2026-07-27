package com.flowledger.labels.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class LabelDtos {
    private LabelDtos() {}

    public record TemplateFieldRequest(
            @NotBlank String fieldType,
            BigDecimal x,
            BigDecimal y,
            BigDecimal width,
            BigDecimal height,
            BigDecimal fontSize,
            String bindingKey,
            Integer zIndex) {}

    public record TemplateRequest(
            @NotBlank String code,
            @NotBlank String name,
            String labelType,
            String templateBody,
            Map<String, Object> canvasJson,
            String paperSize,
            Integer dpi,
            List<TemplateFieldRequest> fields) {}

    public record TemplateResponse(
            UUID id,
            String code,
            String name,
            String labelType,
            String templateBody,
            Map<String, Object> canvasJson,
            String paperSize,
            int dpi,
            List<TemplateFieldResponse> fields,
            Long version) {}

    public record TemplateFieldResponse(
            UUID id,
            String fieldType,
            BigDecimal x,
            BigDecimal y,
            BigDecimal width,
            BigDecimal height,
            BigDecimal fontSize,
            String bindingKey,
            int zIndex) {}

    public record RenderRequest(
            @NotNull UUID templateId, Map<String, String> values, UUID productId) {}

    public record RenderResponse(UUID templateId, byte[] pdfBytes, String contentType) {}

    public record PrintJobRequest(
            @NotNull UUID templateId,
            Map<String, Object> filterJson,
            @Positive Integer copies,
            List<UUID> productIds) {}

    public record PrintJobResponse(
            UUID id,
            String status,
            UUID templateId,
            int copies,
            String outputObjectKey,
            OffsetDateTime createdAt,
            OffsetDateTime completedAt,
            String errorMessage) {}
}
