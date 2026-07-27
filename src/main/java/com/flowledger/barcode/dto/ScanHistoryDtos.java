package com.flowledger.barcode.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public final class ScanHistoryDtos {
    private ScanHistoryDtos() {}

    public record Response(
            UUID id,
            String barcode,
            String source,
            String module,
            UUID resolvedProductId,
            UUID resolvedVariantId,
            boolean success,
            String failureReason,
            OffsetDateTime scannedAt,
            UUID createdBy) {}
}
