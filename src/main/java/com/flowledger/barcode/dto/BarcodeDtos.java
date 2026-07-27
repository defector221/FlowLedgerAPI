package com.flowledger.barcode.dto;

import com.flowledger.retail.dto.RetailDtos.ProductLookupResponse;
import jakarta.validation.constraints.NotBlank;

public final class BarcodeDtos {
    private BarcodeDtos() {}

    public record ScanResolveRequest(
            @NotBlank String barcode, String source, String module) {}

    public record ScanResolveResponse(
            boolean success,
            String barcode,
            String matchType,
            ProductLookupResponse product,
            String failureReason) {}
}
