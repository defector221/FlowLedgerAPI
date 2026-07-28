package com.flowledger.migration.writer;

import static com.flowledger.migration.writer.WriterSupport.*;

import com.flowledger.customer.repository.CustomerRepository;
import com.flowledger.migration.domain.ImportModule;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.sales.dto.SalesDtos.Invoice;
import com.flowledger.sales.dto.SalesDtos.Item;
import com.flowledger.sales.entity.SalesInvoice;
import com.flowledger.sales.repository.SalesInvoiceRepository;
import com.flowledger.sales.service.SalesInvoiceService;
import com.flowledger.warehouse.repository.WarehouseRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SalesInvoiceModuleWriter implements DocumentModuleWriter {
    private final SalesInvoiceService sales;
    private final SalesInvoiceRepository salesRepo;
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final WarehouseRepository warehouses;

    public SalesInvoiceModuleWriter(
            SalesInvoiceService sales,
            SalesInvoiceRepository salesRepo,
            CustomerRepository customers,
            ProductRepository products,
            WarehouseRepository warehouses) {
        this.sales = sales;
        this.salesRepo = salesRepo;
        this.customers = customers;
        this.products = products;
        this.warehouses = warehouses;
    }

    @Override
    public ImportModule module() {
        return ImportModule.SALES_INVOICE;
    }

    @Override
    public String groupKey(Map<String, String> row) {
        return required(row, "invoiceNumber").toUpperCase(Locale.ROOT);
    }

    @Override
    public WriteResult writeDocument(UUID organizationId, List<Map<String, String>> rows) {
        Map<String, String> first = rows.get(0);
        String invoiceNumber = required(first, "invoiceNumber");
        Long dup = salesRepo.countByOrganizationIdAndInvoiceNumberIgnoreCase(organizationId, invoiceNumber);
        if (dup != null && dup > 0) {
            return WriteResult.skipped("Sales invoice already exists: " + invoiceNumber);
        }

        UUID customerId = resolveCustomer(organizationId, first);
        UUID warehouseId = resolveWarehouse(organizationId, first);
        List<Item> items = new ArrayList<>();
        for (Map<String, String> row : rows) {
            String productCode = required(row, "productCode").toUpperCase(Locale.ROOT);
            var product = products.findByOrganizationIdAndSku(organizationId, productCode)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productCode));
            items.add(new Item(
                    product.getId(),
                    str(row, "description") == null ? product.getName() : str(row, "description"),
                    str(row, "hsnSacCode"),
                    decimalOrZero(row, "quantity"),
                    product.getUnitId(),
                    decimalOrZero(row, "rate"),
                    decimal(row, "discountPercent"),
                    decimal(row, "taxRate"),
                    null,
                    null,
                    null,
                    null));
        }

        var detail = sales.createDraft(new Invoice(
                customerId,
                dateOrToday(first, "invoiceDate"),
                null,
                warehouseId,
                null,
                null,
                null,
                null,
                null,
                false,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                "Migration import " + invoiceNumber,
                null,
                null,
                items));

        SalesInvoice entity =
                salesRepo.findByIdAndOrganizationId(detail.id(), organizationId).orElseThrow();
        entity.setInvoiceNumber(invoiceNumber);
        salesRepo.save(entity);
        return WriteResult.imported(entity.getId(), "SALES_INVOICE");
    }

    private UUID resolveCustomer(UUID org, Map<String, String> row) {
        String code = str(row, "customerCode");
        if (code != null) {
            return customers
                    .findByOrganizationIdAndCustomerCode(org, code.toUpperCase(Locale.ROOT))
                    .map(c -> c.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + code));
        }
        String name = str(row, "customerName");
        if (name != null) {
            return customers
                    .findFirstByOrganizationIdAndCustomerNameIgnoreCase(org, name)
                    .map(c -> c.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + name));
        }
        throw new IllegalArgumentException("customerCode or customerName is required");
    }

    private UUID resolveWarehouse(UUID org, Map<String, String> row) {
        String code = str(row, "warehouseCode");
        if (code == null) return null;
        return warehouses.findByOrganizationId(org).stream()
                .filter(w -> w.getWarehouseCode().equalsIgnoreCase(code))
                .map(w -> w.getId())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Warehouse not found: " + code));
    }
}
