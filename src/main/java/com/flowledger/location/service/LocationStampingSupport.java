package com.flowledger.location.service;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.sales.entity.SalesInvoice;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class LocationStampingSupport {
    private final LocationHierarchyService hierarchy;

    public LocationStampingSupport(LocationHierarchyService hierarchy) {
        this.hierarchy = hierarchy;
    }

    public void stampSalesInvoice(SalesInvoice invoice, UUID warehouseId, UUID storeId) {
        UUID effectiveStore = storeId != null ? storeId : hierarchy.resolveStoreId(warehouseId);
        UUID branchId = hierarchy.resolveBranchId(effectiveStore, warehouseId);
        if (branchId == null) {
            branchId = TenantContext.branchId().orElse(hierarchy.defaultBranchId());
        }
        invoice.setBranchId(branchId);
        invoice.setStoreId(effectiveStore);
    }

    public UUID resolveBranch(UUID storeId, UUID warehouseId) {
        UUID branchId = hierarchy.resolveBranchId(storeId, warehouseId);
        if (branchId != null) return branchId;
        return TenantContext.branchId().orElse(hierarchy.defaultBranchId());
    }
}
