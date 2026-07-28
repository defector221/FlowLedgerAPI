package com.flowledger.location.dto;

import com.flowledger.location.domain.LocationScopeType;
import com.flowledger.retail.domain.StoreType;
import com.flowledger.warehouse.domain.WarehouseType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class LocationDtos {
    private LocationDtos() {}

    public record LocationContextRequest(UUID branchId, UUID storeId, UUID warehouseId) {}

    public record LocationContextResponse(
            UUID branchId, UUID storeId, UUID warehouseId, String accessToken, long expiresIn) {}

    public record AccessibleLocationResponse(
            List<BranchOption> branches, List<StoreOption> stores, List<WarehouseOption> warehouses) {}

    public record BranchOption(UUID id, String code, String name, boolean headOffice) {}

    public record StoreOption(UUID id, String code, String name, UUID branchId) {}

    public record WarehouseOption(UUID id, String code, String name, WarehouseType warehouseType, UUID branchId) {}

    public record CashDrawerRequest(
            @NotNull UUID terminalId, @NotBlank String drawerCode, @NotBlank String drawerName, String status) {}

    public record CashDrawerResponse(
            UUID id,
            UUID terminalId,
            String drawerCode,
            String drawerName,
            String status,
            UUID currentCashierId,
            String currentCashierName,
            BigDecimal openingBalance,
            String shiftStatus) {}

    public record TerminalAliasResponse(
            UUID id,
            UUID storeId,
            UUID counterId,
            String code,
            String name,
            String deviceId,
            String status,
            int drawerCount,
            Long version) {}

    public record StoreAliasResponse(
            UUID id,
            String code,
            String name,
            UUID branchId,
            String branchName,
            StoreType storeType,
            UUID warehouseId,
            UUID managerId,
            String address,
            String city,
            String state,
            String phone,
            String email,
            String status,
            int terminalCount,
            Long version) {}

    public record UserLocationAssignmentRequest(
            @NotNull UUID userId,
            @NotNull LocationScopeType scopeType,
            UUID branchId,
            UUID storeId,
            UUID warehouseId,
            UUID terminalId,
            String roleCode) {}

    public record UserLocationAssignmentResponse(
            UUID id,
            UUID userId,
            LocationScopeType scopeType,
            UUID branchId,
            UUID storeId,
            UUID warehouseId,
            UUID terminalId,
            String roleCode,
            boolean active) {}
}
