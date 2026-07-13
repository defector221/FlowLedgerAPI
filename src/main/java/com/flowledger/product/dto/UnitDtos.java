package com.flowledger.product.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public final class UnitDtos {
    private UnitDtos() {}

    public record Create(@NotBlank String code, @NotBlank String name) {}

    public record Response(UUID id, String code, String name, boolean systemUnit, boolean active) {}
}
