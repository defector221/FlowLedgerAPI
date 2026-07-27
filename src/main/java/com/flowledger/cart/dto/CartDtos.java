package com.flowledger.cart.dto;

import static com.flowledger.retail.dto.RetailDtos.*;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class CartDtos {
    private CartDtos() {}

    public record CartScanRequest(
            @NotBlank String barcode,
            @NotNull @Positive BigDecimal quantity,
            @NotNull UUID warehouseId,
            UUID cartId,
            String module,
            String source) {}

    public record CartScanConfirmRequest(
            @NotBlank String barcode,
            @NotNull @Positive BigDecimal quantity,
            @NotNull UUID warehouseId,
            @NotNull UUID batchId,
            UUID cartId,
            String module,
            String source) {}

    public record CartScanResponse(
            String status,
            ProductLookupResponse product,
            AllocatedInventoryResponse allocated,
            List<AllocationCandidateResponse> candidates,
            String reason,
            PosSaleResponse cart) {}
}
