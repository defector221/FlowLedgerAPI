package com.flowledger.location.service;

import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.organization.entity.Branch;
import com.flowledger.organization.repository.BranchRepository;
import com.flowledger.retail.entity.RetailStore;
import com.flowledger.retail.repository.RetailStoreRepository;
import com.flowledger.warehouse.entity.Warehouse;
import com.flowledger.warehouse.repository.WarehouseRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LocationHierarchyService {
    private final BranchRepository branches;
    private final RetailStoreRepository stores;
    private final WarehouseRepository warehouses;

    public LocationHierarchyService(
            BranchRepository branches, RetailStoreRepository stores, WarehouseRepository warehouses) {
        this.branches = branches;
        this.stores = stores;
        this.warehouses = warehouses;
    }

    @Transactional(readOnly = true)
    public UUID resolveBranchId(UUID storeId, UUID warehouseId) {
        if (storeId != null) {
            return stores.findByIdAndOrganizationIdAndDeletedFalse(storeId, org())
                    .map(RetailStore::getBranchId)
                    .orElseThrow(() -> new ResourceNotFoundException("Store not found"));
        }
        if (warehouseId != null) {
            Warehouse w = warehouses
                    .findByIdAndOrganizationId(warehouseId, org())
                    .orElseThrow(() -> new ResourceNotFoundException("Warehouse not found"));
            if (w.getBranchId() != null) return w.getBranchId();
            if (w.getStoreId() != null) {
                return stores.findByIdAndOrganizationIdAndDeletedFalse(w.getStoreId(), org())
                        .map(RetailStore::getBranchId)
                        .orElse(null);
            }
        }
        return defaultBranchId();
    }

    @Transactional(readOnly = true)
    public UUID resolveStoreId(UUID warehouseId) {
        if (warehouseId == null) return null;
        return warehouses
                .findByIdAndOrganizationId(warehouseId, org())
                .map(Warehouse::getStoreId)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public UUID defaultBranchId() {
        return branches.findByOrganizationIdOrderByNameAsc(org()).stream()
                .filter(Branch::isDefaultBranch)
                .map(Branch::getId)
                .findFirst()
                .or(() -> branches.findByOrganizationIdOrderByNameAsc(org()).stream()
                        .findFirst()
                        .map(Branch::getId))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public Branch loadBranch(UUID branchId) {
        return branches.findByIdAndOrganizationId(branchId, org())
                .orElseThrow(() -> new ResourceNotFoundException("Branch not found"));
    }

    private UUID org() {
        return TenantContext.getOrganizationId();
    }
}
