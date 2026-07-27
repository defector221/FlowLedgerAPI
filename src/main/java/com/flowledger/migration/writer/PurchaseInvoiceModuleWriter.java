package com.flowledger.migration.writer;

import static com.flowledger.migration.writer.WriterSupport.*;

import com.flowledger.migration.domain.ImportModule;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.purchase.dto.PurchaseDtos.InvoiceRequest;
import com.flowledger.purchase.dto.PurchaseDtos.Line;
import com.flowledger.purchase.entity.PurchaseInvoice;
import com.flowledger.purchase.service.PurchaseInvoiceService;
import com.flowledger.supplier.repository.SupplierRepository;
import com.flowledger.warehouse.repository.WarehouseRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PurchaseInvoiceModuleWriter implements DocumentModuleWriter {
    @PersistenceContext
    private EntityManager em;

    private final PurchaseInvoiceService purchases;
    private final SupplierRepository suppliers;
    private final ProductRepository products;
    private final WarehouseRepository warehouses;

    public PurchaseInvoiceModuleWriter(
            PurchaseInvoiceService purchases,
            SupplierRepository suppliers,
            ProductRepository products,
            WarehouseRepository warehouses) {
        this.purchases = purchases;
        this.suppliers = suppliers;
        this.products = products;
        this.warehouses = warehouses;
    }

    @Override
    public ImportModule module() {
        return ImportModule.PURCHASE_INVOICE;
    }

    @Override
    public String groupKey(Map<String, String> row) {
        return required(row, "invoiceNumber").toUpperCase(Locale.ROOT);
    }

    @Override
    public WriteResult writeDocument(UUID organizationId, List<Map<String, String>> rows) {
        Map<String, String> first = rows.get(0);
        String invoiceNumber = required(first, "invoiceNumber");
        Long dup = em.createQuery(
                        """
                        select count(i) from PurchaseInvoice i
                        where i.organizationId = :org
                          and lower(i.supplierInvoiceNumber) = lower(:num)
                          and i.status <> 'CANCELLED'
                        """,
                        Long.class)
                .setParameter("org", organizationId)
                .setParameter("num", invoiceNumber)
                .getSingleResult();
        if (dup != null && dup > 0) {
            return WriteResult.skipped("Purchase invoice already exists: " + invoiceNumber);
        }

        UUID supplierId = resolveSupplier(organizationId, first);
        UUID warehouseId = resolveWarehouse(organizationId, first);
        List<Line> lines = new ArrayList<>();
        for (Map<String, String> row : rows) {
            String productCode = required(row, "productCode").toUpperCase(Locale.ROOT);
            var product = products
                    .findByOrganizationIdAndSku(organizationId, productCode)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productCode));
            lines.add(new Line(
                    product.getId(),
                    product.getUnitId(),
                    str(row, "description"),
                    str(row, "hsnSacCode"),
                    decimalOrZero(row, "quantity"),
                    decimalOrZero(row, "rate"),
                    decimal(row, "discountPercent"),
                    decimal(row, "taxRate"),
                    null,
                    null,
                    null,
                    null));
        }

        PurchaseInvoice created = purchases.createStandalone(
                supplierId,
                warehouseId,
                new InvoiceRequest(
                        invoiceNumber,
                        dateOrToday(first, "invoiceDate"),
                        null,
                        null,
                        false,
                        "Migration import",
                        lines));
        return WriteResult.imported(created.getId(), "PURCHASE_INVOICE");
    }

    private UUID resolveSupplier(UUID org, Map<String, String> row) {
        String code = str(row, "supplierCode");
        if (code != null) {
            return suppliers
                    .findByOrganizationIdAndSupplierCode(org, code.toUpperCase(Locale.ROOT))
                    .map(s -> s.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Supplier not found: " + code));
        }
        String name = str(row, "supplierName");
        if (name != null) {
            return suppliers
                    .findFirstByOrganizationIdAndSupplierNameIgnoreCase(org, name)
                    .map(s -> s.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Supplier not found: " + name));
        }
        throw new IllegalArgumentException("supplierCode or supplierName is required");
    }

    private UUID resolveWarehouse(UUID org, Map<String, String> row) {
        String code = str(row, "warehouseCode");
        if (code == null) {
            return warehouses.findByOrganizationId(org).stream()
                    .findFirst()
                    .map(w -> w.getId())
                    .orElse(null);
        }
        return warehouses.findByOrganizationId(org).stream()
                .filter(w -> w.getWarehouseCode().equalsIgnoreCase(code))
                .map(w -> w.getId())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Warehouse not found: " + code));
    }
}
