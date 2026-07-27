package com.flowledger.location.service;

import com.flowledger.auth.dto.LoginResponse;
import com.flowledger.auth.service.AuthService;
import com.flowledger.common.security.SecurityUtils;
import com.flowledger.location.dto.LocationDtos.AccessibleLocationResponse;
import com.flowledger.location.dto.LocationDtos.BranchOption;
import com.flowledger.location.dto.LocationDtos.LocationContextRequest;
import com.flowledger.location.dto.LocationDtos.LocationContextResponse;
import com.flowledger.location.dto.LocationDtos.StoreOption;
import com.flowledger.location.dto.LocationDtos.WarehouseOption;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.organization.entity.Branch;
import com.flowledger.organization.repository.BranchRepository;
import com.flowledger.retail.entity.RetailStore;
import com.flowledger.retail.repository.RetailStoreRepository;
import com.flowledger.warehouse.entity.Warehouse;
import com.flowledger.warehouse.repository.WarehouseRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LocationContextService {
    private final AuthService auth;
    private final LocationScopeService scope;
    private final BranchRepository branches;
    private final RetailStoreRepository stores;
    private final WarehouseRepository warehouses;

    public LocationContextService(
            AuthService auth,
            LocationScopeService scope,
            BranchRepository branches,
            RetailStoreRepository stores,
            WarehouseRepository warehouses) {
        this.auth = auth;
        this.scope = scope;
        this.branches = branches;
        this.stores = stores;
        this.warehouses = warehouses;
    }

    @Transactional(readOnly = true)
    public AccessibleLocationResponse accessibleLocations() {
        UUID org = TenantContext.getOrganizationId();
        List<Branch> branchRows = branches.findByOrganizationIdAndActiveTrueOrderByNameAsc(org);
        List<RetailStore> storeRows = stores.findByOrganizationIdAndDeletedFalseOrderByNameAsc(org);
        List<Warehouse> warehouseRows = warehouses.findByOrganizationId(org);

        if (!scope.isOrgWideAdmin()) {
            Set<UUID> branchIds = scope.accessibleBranchIds();
            Set<UUID> storeIds = scope.accessibleStoreIds();
            Set<UUID> warehouseIds = scope.accessibleWarehouseIds();
            if (!branchIds.isEmpty()) {
                branchRows = branchRows.stream().filter(b -> branchIds.contains(b.getId())).toList();
            }
            if (!storeIds.isEmpty()) {
                storeRows = storeRows.stream().filter(s -> storeIds.contains(s.getId())).toList();
            }
            if (!warehouseIds.isEmpty()) {
                warehouseRows = warehouseRows.stream().filter(w -> warehouseIds.contains(w.getId())).toList();
            }
        }

        return new AccessibleLocationResponse(
                branchRows.stream()
                        .map(b -> new BranchOption(b.getId(), b.getCode(), b.getName(), b.isHeadOffice()))
                        .toList(),
                storeRows.stream()
                        .map(s -> new StoreOption(s.getId(), s.getCode(), s.getName(), s.getBranchId()))
                        .toList(),
                warehouseRows.stream()
                        .map(w -> new WarehouseOption(
                                w.getId(), w.getWarehouseCode(), w.getWarehouseName(), w.getWarehouseType(), w.getBranchId()))
                        .toList());
    }

    @Transactional
    public LocationContextResponse switchContext(LocationContextRequest request) {
        UUID userId = SecurityUtils.currentUserId();
        UUID org = TenantContext.getOrganizationId();
        if (!scope.isOrgWideAdmin()) {
            if (request.branchId() != null) {
                scope.assertAccessibleBranch(request.branchId());
            }
            if (request.storeId() != null) {
                scope.assertAccessibleStore(request.storeId());
            }
            if (request.warehouseId() != null) {
                scope.assertAccessibleWarehouse(request.warehouseId());
            }
        }
        LoginResponse tokens = auth.issueTokensWithLocation(userId, org, request.branchId(), request.storeId(), request.warehouseId());
        return new LocationContextResponse(
                request.branchId(),
                request.storeId(),
                request.warehouseId(),
                tokens.accessToken(),
                tokens.expiresIn());
    }
}
