package com.flowledger.migration.writer;

import static com.flowledger.migration.writer.WriterSupport.*;

import com.flowledger.migration.domain.ImportModule;
import com.flowledger.organization.repository.BranchRepository;
import com.flowledger.warehouse.domain.WarehouseType;
import com.flowledger.warehouse.dto.WarehouseDtos.Create;
import com.flowledger.warehouse.repository.WarehouseRepository;
import com.flowledger.warehouse.service.WarehouseService;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class WarehouseModuleWriter implements ModuleWriter {
    private final WarehouseService warehouses;
    private final WarehouseRepository repo;
    private final BranchRepository branches;

    public WarehouseModuleWriter(
            WarehouseService warehouses, WarehouseRepository repo, BranchRepository branches) {
        this.warehouses = warehouses;
        this.repo = repo;
        this.branches = branches;
    }

    @Override
    public ImportModule module() {
        return ImportModule.WAREHOUSE;
    }

    @Override
    public WriteResult write(UUID organizationId, Map<String, String> row) {
        String code = required(row, "warehouseCode").toUpperCase(Locale.ROOT);
        if (repo.existsByOrganizationIdAndWarehouseCode(organizationId, code)) {
            return WriteResult.skipped("Warehouse already exists: " + code);
        }
        WarehouseType type = WarehouseType.CENTRAL;
        String typeStr = str(row, "warehouseType");
        if (typeStr != null) {
            try {
                type = WarehouseType.valueOf(typeStr.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        UUID branchId = null;
        String branchCode = str(row, "branchCode");
        if (branchCode != null) {
            branchId = branches
                    .findByOrganizationIdAndCode(organizationId, branchCode.toUpperCase(Locale.ROOT))
                    .map(b -> b.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Branch not found: " + branchCode));
        }
        var created = warehouses.create(new Create(
                code,
                required(row, "warehouseName"),
                str(row, "address"),
                null,
                str(row, "phone"),
                bool(row, "defaultWarehouse"),
                type,
                branchId,
                null));
        return WriteResult.imported(created.id(), "WAREHOUSE");
    }
}
