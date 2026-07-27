package com.flowledger.migration.mapping;

import com.flowledger.migration.domain.ImportModule;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class ModuleFieldCatalog {

    private static final Map<ImportModule, List<String>> FIELDS = Map.ofEntries(
            Map.entry(
                    ImportModule.BRANCH,
                    List.of(
                            "branchCode",
                            "branchName",
                            "gstNumber",
                            "pan",
                            "addressLine1",
                            "city",
                            "state",
                            "postalCode",
                            "country",
                            "phone",
                            "email",
                            "headOffice")),
            Map.entry(
                    ImportModule.STORE,
                    List.of(
                            "storeCode",
                            "storeName",
                            "branchCode",
                            "warehouseCode",
                            "storeType",
                            "address",
                            "city",
                            "state",
                            "phone",
                            "email",
                            "status")),
            Map.entry(
                    ImportModule.WAREHOUSE,
                    List.of(
                            "warehouseCode",
                            "warehouseName",
                            "warehouseType",
                            "branchCode",
                            "storeCode",
                            "address",
                            "phone",
                            "defaultWarehouse")),
            Map.entry(
                    ImportModule.CUSTOMER,
                    List.of(
                            "customerCode",
                            "customerName",
                            "companyName",
                            "gstin",
                            "pan",
                            "email",
                            "phone",
                            "billingAddress",
                            "city",
                            "state",
                            "postalCode",
                            "paymentTerms")),
            Map.entry(
                    ImportModule.SUPPLIER,
                    List.of(
                            "supplierCode",
                            "supplierName",
                            "companyName",
                            "gstin",
                            "pan",
                            "email",
                            "phone",
                            "address",
                            "city",
                            "state",
                            "postalCode",
                            "paymentTerms")),
            Map.entry(
                    ImportModule.PRODUCT,
                    List.of(
                            "productCode",
                            "productName",
                            "hsnSacCode",
                            "unitCode",
                            "categoryName",
                            "brandName",
                            "mrp",
                            "sellingPrice",
                            "purchasePrice",
                            "costPrice",
                            "taxRate",
                            "barcode")),
            Map.entry(
                    ImportModule.BARCODE,
                    List.of("productCode", "barcode", "barcodeType", "status")),
            Map.entry(
                    ImportModule.COA,
                    List.of("accountCode", "accountName", "accountType", "parentCode", "openingDebit", "openingCredit")),
            Map.entry(
                    ImportModule.OPENING_STOCK,
                    List.of(
                            "productCode",
                            "warehouseCode",
                            "quantity",
                            "unitCost",
                            "batchNumber",
                            "expiryDate",
                            "transactionDate")),
            Map.entry(
                    ImportModule.OPENING_BALANCE,
                    List.of("accountCode", "openingDebit", "openingCredit", "asOfDate")),
            Map.entry(
                    ImportModule.SALES_INVOICE,
                    List.of(
                            "invoiceNumber",
                            "invoiceDate",
                            "customerCode",
                            "customerName",
                            "warehouseCode",
                            "branchCode",
                            "productCode",
                            "description",
                            "quantity",
                            "rate",
                            "taxRate",
                            "discountPercent",
                            "hsnSacCode")),
            Map.entry(
                    ImportModule.PURCHASE_INVOICE,
                    List.of(
                            "invoiceNumber",
                            "invoiceDate",
                            "supplierCode",
                            "supplierName",
                            "warehouseCode",
                            "branchCode",
                            "productCode",
                            "description",
                            "quantity",
                            "rate",
                            "taxRate",
                            "discountPercent",
                            "hsnSacCode")),
            Map.entry(
                    ImportModule.POS_SALE,
                    List.of(
                            "invoiceNumber",
                            "invoiceDate",
                            "storeCode",
                            "terminalCode",
                            "productCode",
                            "quantity",
                            "rate",
                            "taxRate",
                            "grandTotal")),
            Map.entry(
                    ImportModule.JOURNAL_ENTRY,
                    List.of(
                            "voucherNumber",
                            "voucherDate",
                            "accountCode",
                            "debit",
                            "credit",
                            "narration",
                            "branchCode")),
            Map.entry(
                    ImportModule.PAYMENT,
                    List.of(
                            "paymentNumber",
                            "paymentDate",
                            "supplierCode",
                            "amount",
                            "paymentMode",
                            "reference",
                            "branchCode")),
            Map.entry(
                    ImportModule.RECEIPT,
                    List.of(
                            "paymentNumber",
                            "paymentDate",
                            "customerCode",
                            "amount",
                            "paymentMode",
                            "reference",
                            "branchCode")));

    public List<String> fieldsFor(ImportModule module) {
        return FIELDS.getOrDefault(module, List.of());
    }

    public List<String> requiredFields(ImportModule module) {
        return switch (module) {
            case BRANCH -> List.of("branchCode", "branchName");
            case STORE -> List.of("storeCode", "storeName", "branchCode");
            case WAREHOUSE -> List.of("warehouseCode", "warehouseName");
            case CUSTOMER -> List.of("customerName");
            case SUPPLIER -> List.of("supplierName");
            case PRODUCT -> List.of("productName");
            case BARCODE -> List.of("productCode", "barcode");
            case COA -> List.of("accountCode", "accountName", "accountType");
            case OPENING_STOCK -> List.of("productCode", "warehouseCode", "quantity");
            case OPENING_BALANCE -> List.of("accountCode");
            case SALES_INVOICE -> List.of("invoiceNumber", "invoiceDate", "productCode", "quantity", "rate");
            case PURCHASE_INVOICE -> List.of("invoiceNumber", "invoiceDate", "productCode", "quantity", "rate");
            case POS_SALE -> List.of("invoiceNumber", "storeCode", "productCode", "quantity", "rate");
            case JOURNAL_ENTRY -> List.of("voucherDate", "accountCode");
            case PAYMENT -> List.of("paymentDate", "amount");
            case RECEIPT -> List.of("paymentDate", "amount");
        };
    }
}
