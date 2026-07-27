package com.flowledger.migration.writer;

import static com.flowledger.migration.writer.WriterSupport.*;

import com.flowledger.migration.domain.ImportModule;
import com.flowledger.organization.repository.BranchRepository;
import com.flowledger.retail.domain.StoreType;
import com.flowledger.retail.dto.RetailDtos.StoreRequest;
import com.flowledger.retail.repository.RetailStoreRepository;
import com.flowledger.retail.service.RetailStoreService;
import com.flowledger.warehouse.domain.WarehouseType;
import com.flowledger.warehouse.dto.WarehouseDtos.Create;
import com.flowledger.warehouse.repository.WarehouseRepository;
import com.flowledger.warehouse.service.WarehouseService;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class StoreModuleWriter implements ModuleWriter {
    private final RetailStoreService stores;
    private final RetailStoreRepository storeRepo;
    private final BranchRepository branches;
    private final WarehouseRepository warehouses;
    private final WarehouseService warehouseService;

    public StoreModuleWriter(
            RetailStoreService stores,
            RetailStoreRepository storeRepo,
            BranchRepository branches,
            WarehouseRepository warehouses,
            WarehouseService warehouseService) {
        this.stores = stores;
        this.storeRepo = storeRepo;
        this.branches = branches;
        this.warehouses = warehouses;
        this.warehouseService = warehouseService;
    }

    @Override
    public ImportModule module() {
        return ImportModule.STORE;
    }

    @Override
    public WriteResult write(UUID organizationId, Map<String, String> row) {
        String code = required(row, "storeCode").toUpperCase(Locale.ROOT);
        if (storeRepo.existsByOrganizationIdAndCodeIgnoreCaseAndDeletedFalse(organizationId, code)) {
            return WriteResult.skipped("Store already exists: " + code);
        }
        String branchCode = required(row, "branchCode").toUpperCase(Locale.ROOT);
        var branch = branches
                .findByOrganizationIdAndCode(organizationId, branchCode)
                .orElseThrow(() -> new IllegalArgumentException("Branch not found: " + branchCode));

        UUID warehouseId;
        String whCode = str(row, "warehouseCode");
        if (whCode != null) {
            warehouseId = warehouses.findByOrganizationId(organizationId).stream()
                    .filter(w -> w.getWarehouseCode().equalsIgnoreCase(whCode))
                    .map(w -> w.getId())
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Warehouse not found: " + whCode));
        } else {
            var wh = warehouseService.create(new Create(
                    "WH-" + code,
                    required(row, "storeName") + " Warehouse",
                    str(row, "address"),
                    null,
                    str(row, "phone"),
                    false,
                    WarehouseType.STORE,
                    branch.getId(),
                    null));
            warehouseId = wh.id();
        }

        StoreType type = StoreType.RETAIL;
        String typeStr = str(row, "storeType");
        if (typeStr != null) {
            try {
                type = StoreType.valueOf(typeStr.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }

        var created = stores.createStore(new StoreRequest(
                code,
                required(row, "storeName"),
                branch.getId(),
                null,
                type,
                warehouseId,
                null,
                str(row, "address"),
                str(row, "city"),
                str(row, "state"),
                null,
                null,
                str(row, "phone"),
                str(row, "email"),
                str(row, "status") == null ? "ACTIVE" : str(row, "status"),
                null,
                null,
                null,
                null,
                null,
                null,
                null));
        return WriteResult.imported(created.id(), "STORE");
    }
}
