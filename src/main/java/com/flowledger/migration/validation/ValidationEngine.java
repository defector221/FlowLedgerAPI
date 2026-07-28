package com.flowledger.migration.validation;

import com.flowledger.migration.domain.ImportModule;
import com.flowledger.migration.domain.ImportRowStatus;
import com.flowledger.migration.domain.ValidationSeverity;
import com.flowledger.migration.mapping.ModuleFieldCatalog;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class ValidationEngine {
    private static final Pattern GSTIN = Pattern.compile("^[0-9]{2}[A-Z]{5}[0-9]{4}[A-Z]{1}[1-9A-Z]{1}Z[0-9A-Z]{1}$");
    private static final Pattern PAN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]{1}$");

    private final ModuleFieldCatalog catalog;

    public ValidationEngine(ModuleFieldCatalog catalog) {
        this.catalog = catalog;
    }

    public record RowValidation(ImportRowStatus status, List<Issue> issues) {}

    public record Issue(String field, String code, String message, ValidationSeverity severity) {}

    public List<RowValidation> validate(ImportModule module, List<Map<String, String>> rows) {
        List<RowValidation> results = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();
        List<String> required = catalog.requiredFields(module);

        for (Map<String, String> row : rows) {
            List<Issue> issues = new ArrayList<>();
            for (String field : required) {
                if (blank(row.get(field))) {
                    issues.add(new Issue(field, "REQUIRED", field + " is required", ValidationSeverity.ERROR));
                }
            }
            validateGstin(row, issues);
            validatePan(row, issues);
            validateHsn(row, issues);
            validateQuantity(row, issues);
            String dupeKey = duplicateKey(module, row);
            if (dupeKey != null && !seenKeys.add(dupeKey)) {
                issues.add(new Issue(
                        null, "DUPLICATE_IN_FILE", "Duplicate row in file: " + dupeKey, ValidationSeverity.ERROR));
            }
            boolean hasError = issues.stream().anyMatch(i -> i.severity() == ValidationSeverity.ERROR);
            boolean hasWarn = issues.stream().anyMatch(i -> i.severity() == ValidationSeverity.WARNING);
            ImportRowStatus status =
                    hasError ? ImportRowStatus.ERROR : (hasWarn ? ImportRowStatus.WARNING : ImportRowStatus.OK);
            results.add(new RowValidation(status, issues));
        }
        return results;
    }

    private void validateGstin(Map<String, String> row, List<Issue> issues) {
        for (String field : List.of("gstin", "gstNumber")) {
            String v = row.get(field);
            if (!blank(v) && !GSTIN.matcher(v.trim().toUpperCase(Locale.ROOT)).matches()) {
                issues.add(new Issue(field, "INVALID_GSTIN", "Invalid GSTIN format", ValidationSeverity.ERROR));
            }
        }
    }

    private void validatePan(Map<String, String> row, List<Issue> issues) {
        String v = row.get("pan");
        if (!blank(v) && !PAN.matcher(v.trim().toUpperCase(Locale.ROOT)).matches()) {
            issues.add(new Issue("pan", "INVALID_PAN", "Invalid PAN format", ValidationSeverity.ERROR));
        }
    }

    private void validateHsn(Map<String, String> row, List<Issue> issues) {
        String v = row.get("hsnSacCode");
        if (!blank(v)) {
            String digits = v.replaceAll("\\D", "");
            if (digits.length() < 4 || digits.length() > 8) {
                issues.add(new Issue(
                        "hsnSacCode", "INVALID_HSN", "HSN/SAC should be 4–8 digits", ValidationSeverity.WARNING));
            }
        }
    }

    private void validateQuantity(Map<String, String> row, List<Issue> issues) {
        String v = row.get("quantity");
        if (!blank(v)) {
            try {
                if (new java.math.BigDecimal(v.trim()).signum() < 0) {
                    issues.add(new Issue(
                            "quantity", "NEGATIVE_QTY", "Quantity cannot be negative", ValidationSeverity.ERROR));
                }
            } catch (NumberFormatException e) {
                issues.add(
                        new Issue("quantity", "INVALID_NUMBER", "Quantity must be a number", ValidationSeverity.ERROR));
            }
        }
    }

    private String duplicateKey(ImportModule module, Map<String, String> row) {
        return switch (module) {
            case BRANCH -> nonBlank(row.get("branchCode"));
            case STORE -> nonBlank(row.get("storeCode"));
            case WAREHOUSE -> nonBlank(row.get("warehouseCode"));
            case CUSTOMER -> firstNonBlank(row.get("customerCode"), row.get("gstin"), row.get("phone"));
            case SUPPLIER -> firstNonBlank(row.get("supplierCode"), row.get("gstin"));
            case PRODUCT -> firstNonBlank(row.get("productCode"), row.get("barcode"));
            case BARCODE -> nonBlank(row.get("barcode"));
            case COA -> nonBlank(row.get("accountCode"));
            case SALES_INVOICE, PURCHASE_INVOICE, POS_SALE -> {
                String inv = row.get("invoiceNumber");
                String prod = row.get("productCode");
                yield blank(inv) ? null : inv + "|" + (prod == null ? "" : prod);
            }
            default -> null;
        };
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String nonBlank(String s) {
        return blank(s) ? null : s.trim().toUpperCase(Locale.ROOT);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            String n = nonBlank(v);
            if (n != null) return n;
        }
        return null;
    }
}
