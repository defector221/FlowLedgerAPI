package com.flowledger.migration.writer;

import static com.flowledger.migration.writer.WriterSupport.*;

import com.flowledger.inventory.dto.InventoryDtos.Adjustment;
import com.flowledger.inventory.service.InventoryService;
import com.flowledger.migration.domain.ImportModule;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.warehouse.repository.WarehouseRepository;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OpeningStockModuleWriter implements ModuleWriter {
    private final InventoryService inventory;
    private final ProductRepository products;
    private final WarehouseRepository warehouses;

    public OpeningStockModuleWriter(
            InventoryService inventory, ProductRepository products, WarehouseRepository warehouses) {
        this.inventory = inventory;
        this.products = products;
        this.warehouses = warehouses;
    }

    @Override
    public ImportModule module() {
        return ImportModule.OPENING_STOCK;
    }

    @Override
    public WriteResult write(UUID organizationId, Map<String, String> row) {
        String productCode = required(row, "productCode").toUpperCase(Locale.ROOT);
        String whCode = required(row, "warehouseCode");
        var product = products
                .findByOrganizationIdAndSku(organizationId, productCode)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productCode));
        var warehouse = warehouses.findByOrganizationId(organizationId).stream()
                .filter(w -> w.getWarehouseCode().equalsIgnoreCase(whCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Warehouse not found: " + whCode));
        var tx = inventory.openingStock(new Adjustment(
                product.getId(),
                warehouse.getId(),
                decimalOrZero(row, "quantity"),
                "Migration opening stock",
                decimal(row, "unitCost")));
        return WriteResult.imported(tx.getId(), "OPENING_STOCK");
    }
}
