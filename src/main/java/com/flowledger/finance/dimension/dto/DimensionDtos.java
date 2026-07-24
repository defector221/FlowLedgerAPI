package com.flowledger.finance.dimension.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public final class DimensionDtos {
    private DimensionDtos() {}

    public record DimensionRequest(@NotBlank String code, @NotBlank String name, Boolean active) {}

    public record DimensionResponse(UUID id, String code, String name, boolean active) {}
}
