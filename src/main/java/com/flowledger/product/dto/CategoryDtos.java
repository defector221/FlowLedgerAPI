package com.flowledger.product.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public final class CategoryDtos {
    private CategoryDtos() {}

    public record Create(@NotBlank String name, String description, UUID parentId) {}

    public record Update(@NotBlank String name, String description, UUID parentId, Boolean active) {}

    public record Response(
            UUID id, String name, String description, UUID parentId, String parentName, boolean active) {}
}
