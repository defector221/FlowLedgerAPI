package com.flowledger.migration.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.flowledger.migration.domain.ImportModule;
import com.flowledger.migration.domain.ImportRowStatus;
import com.flowledger.migration.mapping.ModuleFieldCatalog;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ValidationEngineTest {
    private final ValidationEngine engine = new ValidationEngine(new ModuleFieldCatalog());

    @Test
    void flagsRequiredAndDuplicateSku() {
        var results = engine.validate(
                ImportModule.PRODUCT,
                List.of(
                        Map.of("productName", "A", "productCode", "SKU1"),
                        Map.of("productName", "B", "productCode", "SKU1"),
                        Map.of("productCode", "SKU2")));
        assertEquals(ImportRowStatus.OK, results.get(0).status());
        assertEquals(ImportRowStatus.ERROR, results.get(1).status());
        assertEquals(ImportRowStatus.ERROR, results.get(2).status()); // missing productName
    }

    @Test
    void validatesGstinFormat() {
        var results = engine.validate(
                ImportModule.CUSTOMER,
                List.of(Map.of("customerName", "Acme", "gstin", "INVALID")));
        assertEquals(ImportRowStatus.ERROR, results.get(0).status());
    }
}
