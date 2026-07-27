package com.flowledger.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ProductIdentificationDtos {
    private ProductIdentificationDtos() {}

    public record QrCodeRequest(String payloadTemplate) {}

    public record QrCodeResponse(
            UUID id,
            UUID productId,
            String payloadTemplate,
            String payloadResolved,
            String format,
            String objectKeyPng,
            String objectKeySvg,
            String pngUrl,
            String svgUrl,
            OffsetDateTime updatedAt) {}

    public record ImageResponse(
            UUID id,
            UUID productId,
            String objectKey,
            String thumbnailKey,
            int sortOrder,
            boolean primary,
            String mimeType,
            Long sizeBytes,
            String url,
            String thumbnailUrl,
            OffsetDateTime createdAt) {}

    public record ReorderImagesRequest(List<UUID> imageIds) {}

    public record ImportPreviewResponse(UUID jobId, String status, Object preview) {}

    public record ImportCommitRequest(UUID jobId) {}

    public record ImportJobResponse(
            UUID id, String status, String fileName, Map<String, Object> result, String errorMessage) {}

    public record ExportBarcodesRequest(List<UUID> productIds, String format) {}
}
