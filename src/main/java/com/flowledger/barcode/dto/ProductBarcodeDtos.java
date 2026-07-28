package com.flowledger.barcode.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class ProductBarcodeDtos {
    private ProductBarcodeDtos() {}

    public record CreateBarcodeRequest(
            @NotBlank @Size(max = 150) String barcode, String barcodeType, Boolean primary, UUID variantId) {}

    public record GenerateBarcodeRequest(String barcodeType, Boolean primary, String reason) {}

    public record RegenerateBarcodeRequest(String reason) {}

    public record BulkGenerateRequest(List<UUID> productIds) {}

    public record BarcodeResponse(
            UUID id,
            UUID productId,
            UUID variantId,
            String barcode,
            String barcodeType,
            boolean primary,
            String status,
            boolean generated,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt) {}

    public record BarcodeHistoryResponse(
            UUID id,
            UUID productId,
            UUID barcodeId,
            String oldBarcode,
            String newBarcode,
            String operation,
            String reason,
            UUID createdBy,
            OffsetDateTime createdAt) {}

    /** @deprecated Prefer {@link BulkGenerateResultResponse}; kept for older clients. */
    public record BulkGenerateJobResponse(UUID jobId, String status) {}

    public record BulkGenerateResultResponse(
            int requested, int generated, int skipped, int failed, List<String> errors) {}

    public record BarcodeSheetItem(UUID productId, String sku, String name, String barcode, String barcodeType) {}
}
