package com.flowledger.warehouse.dto;

import com.flowledger.warehouse.domain.WarehouseType;
import jakarta.validation.constraints.*;
import java.util.*;

public final class WarehouseDtos {
    private WarehouseDtos() {}

    public record Create(
            String warehouseCode,
            @NotBlank String warehouseName,
            String address,
            String contactPerson,
            String phone,
            Boolean defaultWarehouse,
            WarehouseType warehouseType,
            UUID branchId,
            UUID storeId) {}

    public record Update(
            @NotBlank String warehouseName,
            String address,
            String contactPerson,
            String phone,
            Boolean active,
            WarehouseType warehouseType,
            UUID branchId,
            UUID storeId) {}

    public record Response(
            UUID id,
            String warehouseCode,
            String warehouseName,
            String address,
            String contactPerson,
            String phone,
            boolean defaultWarehouse,
            boolean active,
            WarehouseType warehouseType,
            UUID branchId,
            UUID storeId) {}
}
