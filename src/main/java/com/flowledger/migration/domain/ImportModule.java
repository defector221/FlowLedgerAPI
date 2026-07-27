package com.flowledger.migration.domain;

import java.util.List;

public enum ImportModule {
    BRANCH(List.of(), "Branches"),
    STORE(List.of("BRANCH", "WAREHOUSE"), "Stores"),
    WAREHOUSE(List.of(), "Warehouses"),
    CUSTOMER(List.of(), "Customers"),
    SUPPLIER(List.of(), "Suppliers"),
    PRODUCT(List.of(), "Products"),
    BARCODE(List.of("PRODUCT"), "Barcodes"),
    COA(List.of(), "Chart of Accounts"),
    OPENING_STOCK(List.of("PRODUCT", "WAREHOUSE"), "Opening Stock"),
    OPENING_BALANCE(List.of("COA"), "Opening Balances"),
    SALES_INVOICE(List.of("CUSTOMER", "PRODUCT"), "Sales Invoices"),
    PURCHASE_INVOICE(List.of("SUPPLIER", "PRODUCT"), "Purchase Invoices"),
    POS_SALE(List.of("STORE", "PRODUCT"), "POS Sales"),
    JOURNAL_ENTRY(List.of("COA"), "Journal Entries"),
    PAYMENT(List.of("SUPPLIER"), "Payments"),
    RECEIPT(List.of("CUSTOMER"), "Receipts");

    private final List<String> dependsOn;
    private final String displayName;

    ImportModule(List<String> dependsOn, String displayName) {
        this.dependsOn = dependsOn;
        this.displayName = displayName;
    }

    public List<String> dependsOn() {
        return dependsOn;
    }

    public String displayName() {
        return displayName;
    }
}
