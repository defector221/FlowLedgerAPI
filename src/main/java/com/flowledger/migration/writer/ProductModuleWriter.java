package com.flowledger.migration.writer;

import static com.flowledger.migration.writer.WriterSupport.*;

import com.flowledger.migration.domain.ImportModule;
import com.flowledger.product.dto.ProductDtos.Create;
import com.flowledger.product.entity.Unit;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.product.repository.UnitRepository;
import com.flowledger.product.service.ProductService;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ProductModuleWriter implements ModuleWriter {
    private final ProductService products;
    private final ProductRepository repo;
    private final UnitRepository units;

    public ProductModuleWriter(ProductService products, ProductRepository repo, UnitRepository units) {
        this.products = products;
        this.repo = repo;
        this.units = units;
    }

    @Override
    public ImportModule module() {
        return ImportModule.PRODUCT;
    }

    @Override
    public WriteResult write(UUID organizationId, Map<String, String> row) {
        String sku = str(row, "productCode");
        if (sku != null && repo.existsByOrganizationIdAndSku(organizationId, sku.toUpperCase(Locale.ROOT))) {
            return WriteResult.skipped("Product already exists: " + sku);
        }
        UUID unitId = resolveUnit(organizationId, str(row, "unitCode"));
        var created = products.create(new Create(
                sku == null ? null : sku.toUpperCase(Locale.ROOT),
                required(row, "productName"),
                unitId,
                null,
                str(row, "barcode"),
                null,
                null,
                str(row, "brandName"),
                str(row, "hsnSacCode"),
                decimal(row, "purchasePrice"),
                decimal(row, "sellingPrice"),
                decimal(row, "mrp"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null));
        return WriteResult.imported(created.id(), "PRODUCT");
    }

    private UUID resolveUnit(UUID org, String code) {
        var all = units.findBySystemUnitTrueOrOrganizationId(org);
        if (code != null) {
            return all.stream()
                    .filter(u -> code.equalsIgnoreCase(u.getCode()))
                    .map(Unit::getId)
                    .findFirst()
                    .orElseGet(() -> all.stream().findFirst().map(Unit::getId).orElse(null));
        }
        return all.stream().findFirst().map(Unit::getId).orElse(null);
    }
}
