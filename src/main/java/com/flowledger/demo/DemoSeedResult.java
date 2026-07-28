package com.flowledger.demo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record DemoSeedResult(
        String scenario,
        String status,
        UUID organizationId,
        String organizationName,
        String adminEmail,
        String message,
        int estimatedMinutes,
        long durationMs,
        Map<String, Object> counts,
        Map<String, Boolean> verification) {

    public static final String COUNT_TAX_JURISDICTIONS = "taxJurisdictions";
    public static final String COUNT_TAX_CATEGORIES = "taxCategories";
    public static final String COUNT_TAX_RULES = "taxRules";
    public static final String COUNT_HSN_SAC_CODES = "hsnSacCodes";
    public static final String COUNT_PRODUCT_CATEGORY_TAX_MAPPINGS = "productCategoryTaxMappings";
    public static final String COUNT_PRODUCTS_WITH_TAX_MAPPING = "productsWithTaxMapping";
    public static final String COUNT_INTRA_STATE_INVOICES = "intraStateInvoices";
    public static final String COUNT_INTER_STATE_INVOICES = "interStateInvoices";

    public static void putTaxCounts(Map<String, Object> counts, DemoSeedContext ctx) {
        counts.put(COUNT_TAX_JURISDICTIONS, ctx.getMeta().getOrDefault(COUNT_TAX_JURISDICTIONS, 0));
        counts.put(COUNT_TAX_CATEGORIES, ctx.getTaxCategoryIds().size());
        counts.put(COUNT_TAX_RULES, ctx.getTaxRuleByCode().size());
        counts.put(COUNT_HSN_SAC_CODES, ctx.getHsnSacByCategory().size());
        counts.put(
                COUNT_PRODUCT_CATEGORY_TAX_MAPPINGS,
                ctx.getMeta().getOrDefault(COUNT_PRODUCT_CATEGORY_TAX_MAPPINGS, 0));
        counts.put(COUNT_PRODUCTS_WITH_TAX_MAPPING, ctx.getProductsWithTaxMapping());
        counts.put(COUNT_INTRA_STATE_INVOICES, ctx.getIntraStateInvoices());
        counts.put(COUNT_INTER_STATE_INVOICES, ctx.getInterStateInvoices());
    }

    public static Map<String, Object> baseCounts(DemoSeedContext ctx) {
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.put("branches", ctx.getBranchIds().size());
        counts.put("warehouses", ctx.getWarehouseIds().size());
        counts.put("stores", ctx.getStoreIds().size());
        counts.put("terminals", ctx.getTerminalIds().size());
        counts.put("products", ctx.getProductIds().size());
        counts.put("customers", ctx.getCustomerIds().size());
        counts.put("suppliers", ctx.getSupplierIds().size());
        counts.put("supplierCatalogLinks", ctx.getMeta().getOrDefault("supplierCatalogLinks", 0));
        counts.put("imagesUploaded", ctx.getMeta().getOrDefault("imagesUploaded", 0));
        counts.put("imagesReused", ctx.getMeta().getOrDefault("imagesReused", 0));
        putTaxCounts(counts, ctx);
        return counts;
    }
}
