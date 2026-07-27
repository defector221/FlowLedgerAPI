package com.flowledger.warehouse.service;

import com.flowledger.common.service.OrganizationScopedService;
import com.flowledger.common.util.EntityCodeGenerator;
import com.flowledger.location.service.LocationHierarchyService;
import com.flowledger.location.service.LocationScopeService;
import com.flowledger.retail.repository.RetailStoreRepository;
import com.flowledger.warehouse.domain.WarehouseType;
import com.flowledger.warehouse.dto.WarehouseDtos.*;
import com.flowledger.warehouse.entity.Warehouse;
import com.flowledger.warehouse.mapper.WarehouseMapper;
import com.flowledger.warehouse.repository.WarehouseRepository;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional
public class WarehouseService extends OrganizationScopedService {
    private final WarehouseRepository repo;
    private final WarehouseMapper mapper;
    private final RetailStoreRepository stores;
    private final LocationHierarchyService hierarchy;
    private final LocationScopeService scope;

    public WarehouseService(
            WarehouseRepository repo,
            WarehouseMapper mapper,
            RetailStoreRepository stores,
            LocationHierarchyService hierarchy,
            LocationScopeService scope) {
        this.repo = repo;
        this.mapper = mapper;
        this.stores = stores;
        this.hierarchy = hierarchy;
        this.scope = scope;
    }

    public Response create(Create dto) {
        UUID org = orgId();
        String code = resolveCode(org, dto.warehouseCode(), dto.warehouseName());
        if (repo.existsByOrganizationIdAndWarehouseCode(org, code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Warehouse code already exists");
        }
        if (Boolean.TRUE.equals(dto.defaultWarehouse())) {
            repo.clearDefault(org);
        }
        Warehouse warehouse = mapper.toEntity(dto);
        warehouse.setWarehouseCode(code);
        warehouse.setOrganizationId(org);
        if (dto.defaultWarehouse() != null) {
            warehouse.setDefaultWarehouse(dto.defaultWarehouse());
        }
        applyTypeRules(warehouse, dto.warehouseType(), dto.branchId(), dto.storeId());
        return mapper.toResponse(repo.save(warehouse));
    }

    private String resolveCode(UUID org, String provided, String name) {
        if (provided != null && !provided.isBlank()) {
            return provided.trim().toUpperCase(Locale.ROOT);
        }
        return EntityCodeGenerator.uniqueFromName(
                name, "WH", candidate -> repo.existsByOrganizationIdAndWarehouseCode(org, candidate));
    }

    @Transactional(readOnly = true)
    public Response get(UUID id) {
        scope.assertAccessibleWarehouse(id);
        return mapper.toResponse(load(id));
    }

    public Response update(UUID id, Update dto) {
        Warehouse warehouse = load(id);
        scope.assertAccessibleWarehouse(id);
        mapper.update(dto, warehouse);
        if (dto.active() != null) {
            warehouse.setActive(dto.active());
        }
        if (dto.warehouseType() != null || dto.branchId() != null || dto.storeId() != null) {
            applyTypeRules(
                    warehouse,
                    dto.warehouseType() != null ? dto.warehouseType() : warehouse.getWarehouseType(),
                    dto.branchId() != null ? dto.branchId() : warehouse.getBranchId(),
                    dto.storeId() != null ? dto.storeId() : warehouse.getStoreId());
        }
        return mapper.toResponse(repo.save(warehouse));
    }

    @Transactional(readOnly = true)
    public List<Response> list(WarehouseType type, UUID branchId) {
        List<Warehouse> rows;
        if (type != null) {
            rows = repo.findByOrganizationIdAndWarehouseType(orgId(), type);
        } else if (branchId != null) {
            scope.assertAccessibleBranch(branchId);
            rows = repo.findByOrganizationIdAndBranchId(orgId(), branchId);
        } else {
            rows = repo.findByOrganizationId(orgId());
        }
        if (!scope.isOrgWideAdmin()) {
            var allowed = scope.accessibleWarehouseIds();
            if (!allowed.isEmpty()) {
                rows = rows.stream().filter(w -> allowed.contains(w.getId())).toList();
            }
        }
        return rows.stream().map(mapper::toResponse).toList();
    }

    private void applyTypeRules(Warehouse warehouse, WarehouseType type, UUID branchId, UUID storeId) {
        WarehouseType effective = type != null ? type : WarehouseType.CENTRAL;
        warehouse.setWarehouseType(effective);
        switch (effective) {
            case CENTRAL -> {
                warehouse.setBranchId(null);
                warehouse.setStoreId(null);
            }
            case BRANCH -> {
                if (branchId == null) {
                    branchId = hierarchy.defaultBranchId();
                }
                if (branchId == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Branch is required for branch warehouse");
                }
                warehouse.setBranchId(branchId);
                warehouse.setStoreId(null);
            }
            case STORE -> {
                if (storeId == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Store is required for store warehouse");
                }
                var store = stores.findByIdAndOrganizationIdAndDeletedFalse(storeId, orgId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Store not found"));
                warehouse.setStoreId(storeId);
                warehouse.setBranchId(store.getBranchId());
            }
            case TRANSIT -> {
                warehouse.setBranchId(branchId);
                warehouse.setStoreId(null);
            }
            default -> {}
        }
    }

    private Warehouse load(UUID id) {
        return required(repo.findByIdAndOrganizationId(id, orgId()), "Warehouse");
    }
}
