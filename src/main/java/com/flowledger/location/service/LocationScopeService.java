package com.flowledger.location.service;

import com.flowledger.common.tenant.TenantContext;
import com.flowledger.location.domain.LocationScopeType;
import com.flowledger.location.entity.UserLocationAssignment;
import com.flowledger.location.repository.UserLocationAssignmentRepository;
import com.flowledger.organization.repository.BranchRepository;
import com.flowledger.retail.entity.RetailStore;
import com.flowledger.retail.repository.RetailStoreRepository;
import com.flowledger.warehouse.entity.Warehouse;
import com.flowledger.warehouse.repository.WarehouseRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class LocationScopeService {
    private final UserLocationAssignmentRepository assignments;
    private final BranchRepository branches;
    private final RetailStoreRepository stores;
    private final WarehouseRepository warehouses;

    public LocationScopeService(
            UserLocationAssignmentRepository assignments,
            BranchRepository branches,
            RetailStoreRepository stores,
            WarehouseRepository warehouses) {
        this.assignments = assignments;
        this.branches = branches;
        this.stores = stores;
        this.warehouses = warehouses;
    }

    @Transactional(readOnly = true)
    public boolean isOrgWideAdmin() {
        if (hasRole("ORGANIZATION_ADMIN") || hasRole("RETAIL_ADMIN")) {
            return true;
        }
        return assignments.findByOrganizationIdAndUserIdAndActiveTrue(org(), userId()).stream()
                        .anyMatch(a -> a.getScopeType() == LocationScopeType.ORGANIZATION)
                || assignments
                                .findByOrganizationIdAndUserIdAndActiveTrue(org(), userId())
                                .isEmpty()
                        && hasRole("ORGANIZATION_ADMIN");
    }

    @Transactional(readOnly = true)
    public void assertAccessibleBranch(UUID branchId) {
        if (branchId == null || isOrgWideAdmin()) return;
        if (!accessibleBranchIds().contains(branchId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Branch access denied");
        }
    }

    @Transactional(readOnly = true)
    public void assertAccessibleStore(UUID storeId) {
        if (storeId == null || isOrgWideAdmin()) return;
        if (!accessibleStoreIds().contains(storeId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Store access denied");
        }
    }

    @Transactional(readOnly = true)
    public void assertAccessibleWarehouse(UUID warehouseId) {
        if (warehouseId == null || isOrgWideAdmin()) return;
        if (!accessibleWarehouseIds().contains(warehouseId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Warehouse access denied");
        }
    }

    @Transactional(readOnly = true)
    public Set<UUID> accessibleBranchIds() {
        if (isOrgWideAdmin()) return Set.of();
        Set<UUID> ids = new HashSet<>();
        for (UserLocationAssignment a : activeAssignments()) {
            switch (a.getScopeType()) {
                case ORGANIZATION -> {
                    return Set.of();
                }
                case BRANCH -> {
                    if (a.getBranchId() != null) ids.add(a.getBranchId());
                }
                case STORE -> {
                    store(a.getStoreId()).ifPresent(s -> ids.add(s.getBranchId()));
                }
                case WAREHOUSE -> {
                    warehouse(a.getWarehouseId()).ifPresent(w -> {
                        if (w.getBranchId() != null) ids.add(w.getBranchId());
                    });
                }
                case TERMINAL -> {
                    // terminal scope inherits store branch via terminal lookup elsewhere
                }
                default -> {}
            }
        }
        return ids;
    }

    @Transactional(readOnly = true)
    public Set<UUID> accessibleStoreIds() {
        if (isOrgWideAdmin()) return Set.of();
        Set<UUID> ids = new HashSet<>();
        Set<UUID> branchIds = accessibleBranchIds();
        if (branchIds.isEmpty() && !activeAssignments().isEmpty() && !hasOnlyBranchScope()) {
            // org-wide from assignments empty set above
        }
        for (UserLocationAssignment a : activeAssignments()) {
            switch (a.getScopeType()) {
                case ORGANIZATION -> {
                    return Set.of();
                }
                case STORE, TERMINAL -> {
                    if (a.getStoreId() != null) ids.add(a.getStoreId());
                }
                case BRANCH -> {
                    if (a.getBranchId() != null) {
                        stores.findByOrganizationIdAndBranchIdAndDeletedFalseOrderByNameAsc(org(), a.getBranchId())
                                .forEach(s -> ids.add(s.getId()));
                    }
                }
                default -> {}
            }
        }
        if (!branchIds.isEmpty()) {
            for (UUID branchId : branchIds) {
                stores.findByOrganizationIdAndBranchIdAndDeletedFalseOrderByNameAsc(org(), branchId)
                        .forEach(s -> ids.add(s.getId()));
            }
        }
        return ids;
    }

    @Transactional(readOnly = true)
    public Set<UUID> accessibleWarehouseIds() {
        if (isOrgWideAdmin()) return Set.of();
        Set<UUID> ids = new HashSet<>();
        for (UserLocationAssignment a : activeAssignments()) {
            switch (a.getScopeType()) {
                case ORGANIZATION -> {
                    return Set.of();
                }
                case WAREHOUSE -> {
                    if (a.getWarehouseId() != null) ids.add(a.getWarehouseId());
                }
                case STORE -> {
                    store(a.getStoreId()).ifPresent(s -> ids.add(s.getWarehouseId()));
                }
                case BRANCH -> {
                    if (a.getBranchId() != null) {
                        warehouses
                                .findByOrganizationIdAndBranchId(org(), a.getBranchId())
                                .forEach(w -> ids.add(w.getId()));
                    }
                }
                default -> {}
            }
        }
        return ids;
    }

    private boolean hasOnlyBranchScope() {
        return activeAssignments().stream().allMatch(a -> a.getScopeType() == LocationScopeType.BRANCH);
    }

    private List<UserLocationAssignment> activeAssignments() {
        return assignments.findByOrganizationIdAndUserIdAndActiveTrue(org(), userId());
    }

    private Optional<RetailStore> store(UUID id) {
        if (id == null) return Optional.empty();
        return stores.findByIdAndOrganizationIdAndDeletedFalse(id, org());
    }

    private Optional<Warehouse> warehouse(UUID id) {
        if (id == null) return Optional.empty();
        return warehouses.findByIdAndOrganizationId(id, org());
    }

    private UUID org() {
        return TenantContext.getOrganizationId();
    }

    private UUID userId() {
        return TenantContext.userId().orElseThrow(() -> new IllegalStateException("User context is not set"));
    }

    private boolean hasRole(String role) {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_" + role));
    }
}
