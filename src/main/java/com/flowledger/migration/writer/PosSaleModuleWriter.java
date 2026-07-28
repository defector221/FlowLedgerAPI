package com.flowledger.migration.writer;

import static com.flowledger.migration.writer.WriterSupport.*;

import com.flowledger.migration.domain.ImportModule;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.retail.dto.RetailDtos.PosLineRequest;
import com.flowledger.retail.dto.RetailDtos.PosSaleRequest;
import com.flowledger.retail.repository.RetailStoreRepository;
import com.flowledger.retail.repository.RetailTerminalRepository;
import com.flowledger.retail.service.PosSaleService;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PosSaleModuleWriter implements DocumentModuleWriter {
    private final PosSaleService posSales;
    private final RetailStoreRepository stores;
    private final RetailTerminalRepository terminals;
    private final ProductRepository products;

    public PosSaleModuleWriter(
            PosSaleService posSales,
            RetailStoreRepository stores,
            RetailTerminalRepository terminals,
            ProductRepository products) {
        this.posSales = posSales;
        this.stores = stores;
        this.terminals = terminals;
        this.products = products;
    }

    @Override
    public ImportModule module() {
        return ImportModule.POS_SALE;
    }

    @Override
    public String groupKey(Map<String, String> row) {
        return required(row, "invoiceNumber").toUpperCase(Locale.ROOT);
    }

    @Override
    public WriteResult writeDocument(UUID organizationId, List<Map<String, String>> rows) {
        Map<String, String> first = rows.get(0);
        String storeCode = required(first, "storeCode");
        var store = stores.findByOrganizationIdAndDeletedFalseOrderByNameAsc(organizationId).stream()
                .filter(s -> storeCode.equalsIgnoreCase(s.getCode()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Store not found: " + storeCode));

        UUID terminalId = null;
        String terminalCode = str(first, "terminalCode");
        if (terminalCode != null) {
            terminalId =
                    terminals
                            .findByOrganizationIdAndStoreIdAndDeletedFalseOrderByNameAsc(organizationId, store.getId())
                            .stream()
                            .filter(t -> terminalCode.equalsIgnoreCase(t.getCode()))
                            .map(t -> t.getId())
                            .findFirst()
                            .orElse(null);
        }

        var sale = posSales.createDraft(
                new PosSaleRequest(store.getId(), null, terminalId, null, null, null, "Migration " + groupKey(first)));

        for (Map<String, String> row : rows) {
            String productCode = required(row, "productCode").toUpperCase(Locale.ROOT);
            var product = products.findByOrganizationIdAndSku(organizationId, productCode)
                    .orElseThrow(() -> new IllegalArgumentException("Product not found: " + productCode));
            posSales.addLine(
                    sale.id(),
                    new PosLineRequest(
                            product.getId(),
                            null,
                            product.getName(),
                            null,
                            decimalOrZero(row, "quantity"),
                            decimalOrZero(row, "rate"),
                            null,
                            decimal(row, "taxRate"),
                            store.getWarehouseId(),
                            null,
                            null));
        }
        return WriteResult.imported(sale.id(), "POS_SALE");
    }
}
