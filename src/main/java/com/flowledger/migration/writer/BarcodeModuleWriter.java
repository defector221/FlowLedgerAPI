package com.flowledger.migration.writer;

import static com.flowledger.migration.writer.WriterSupport.*;

import com.flowledger.barcode.dto.ProductBarcodeDtos.CreateBarcodeRequest;
import com.flowledger.barcode.service.ProductBarcodeManagementService;
import com.flowledger.migration.domain.ImportModule;
import com.flowledger.product.repository.ProductRepository;
import com.flowledger.retail.repository.RetailProductBarcodeRepository;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class BarcodeModuleWriter implements ModuleWriter {
    private final ProductBarcodeManagementService barcodes;
    private final ProductRepository products;
    private final RetailProductBarcodeRepository barcodeRepo;

    public BarcodeModuleWriter(
            ProductBarcodeManagementService barcodes,
            ProductRepository products,
            RetailProductBarcodeRepository barcodeRepo) {
        this.barcodes = barcodes;
        this.products = products;
        this.barcodeRepo = barcodeRepo;
    }

    @Override
    public ImportModule module() {
        return ImportModule.BARCODE;
    }

    @Override
    public WriteResult write(UUID organizationId, Map<String, String> row) {
        String code = required(row, "productCode").toUpperCase(Locale.ROOT);
        String barcode = required(row, "barcode");
        var product = products
                .findByOrganizationIdAndSku(organizationId, code)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + code));
        if (barcodeRepo.existsActiveByOrganizationIdAndBarcode(organizationId, barcode)) {
            return WriteResult.skipped("Barcode already exists: " + barcode);
        }
        var created = barcodes.create(
                product.getId(),
                new CreateBarcodeRequest(barcode, str(row, "barcodeType"), true, null));
        return WriteResult.imported(created.id(), "BARCODE");
    }
}
