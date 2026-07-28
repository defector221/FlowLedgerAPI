package com.flowledger.demo;

import com.flowledger.demo.scenario.DemoBlueprint;
import com.flowledger.demo.scenario.DemoScenario;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class DemoSeedContext {
    private DemoScenario scenario;
    private DemoBlueprint blueprint;
    private UUID organizationId;
    private UUID adminUserId;
    private final List<UUID> branchIds = new ArrayList<>();
    private final List<UUID> warehouseIds = new ArrayList<>();
    private final List<UUID> storeIds = new ArrayList<>();
    private final List<UUID> terminalIds = new ArrayList<>();
    private final List<UUID> productIds = new ArrayList<>();
    private final List<UUID> customerIds = new ArrayList<>();
    private final List<UUID> supplierIds = new ArrayList<>();
    private final List<UUID> unitIds = new ArrayList<>();
    private final List<UUID> taxRateIds = new ArrayList<>();
    private final Map<String, UUID> taxCategoryIds = new HashMap<>();
    private final Map<String, UUID> taxRuleByCode = new HashMap<>();
    private final Map<String, String> hsnSacByCategory = new HashMap<>();
    /** storeId → linked store warehouseId */
    private final Map<UUID, UUID> storeWarehouseIds = new HashMap<>();

    private final Map<String, Object> meta = new HashMap<>();
    private final Map<String, Boolean> checks = new HashMap<>();
    private int intraStateInvoices;
    private int interStateInvoices;
    private int productsWithTaxMapping;

    public DemoScenario getScenario() {
        return scenario;
    }

    public void setScenario(DemoScenario scenario) {
        this.scenario = scenario;
    }

    public DemoBlueprint getBlueprint() {
        return blueprint;
    }

    public void setBlueprint(DemoBlueprint blueprint) {
        this.blueprint = blueprint;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public UUID getAdminUserId() {
        return adminUserId;
    }

    public void setAdminUserId(UUID adminUserId) {
        this.adminUserId = adminUserId;
    }

    public List<UUID> getBranchIds() {
        return branchIds;
    }

    public List<UUID> getWarehouseIds() {
        return warehouseIds;
    }

    public List<UUID> getStoreIds() {
        return storeIds;
    }

    public List<UUID> getTerminalIds() {
        return terminalIds;
    }

    public List<UUID> getProductIds() {
        return productIds;
    }

    public List<UUID> getCustomerIds() {
        return customerIds;
    }

    public List<UUID> getSupplierIds() {
        return supplierIds;
    }

    public List<UUID> getUnitIds() {
        return unitIds;
    }

    public List<UUID> getTaxRateIds() {
        return taxRateIds;
    }

    public Map<String, UUID> getTaxCategoryIds() {
        return taxCategoryIds;
    }

    public Map<String, UUID> getTaxRuleByCode() {
        return taxRuleByCode;
    }

    public Map<String, String> getHsnSacByCategory() {
        return hsnSacByCategory;
    }

    public int getIntraStateInvoices() {
        return intraStateInvoices;
    }

    public void setIntraStateInvoices(int intraStateInvoices) {
        this.intraStateInvoices = intraStateInvoices;
    }

    public int getInterStateInvoices() {
        return interStateInvoices;
    }

    public void setInterStateInvoices(int interStateInvoices) {
        this.interStateInvoices = interStateInvoices;
    }

    public int getProductsWithTaxMapping() {
        return productsWithTaxMapping;
    }

    public void setProductsWithTaxMapping(int productsWithTaxMapping) {
        this.productsWithTaxMapping = productsWithTaxMapping;
    }

    public Map<UUID, UUID> getStoreWarehouseIds() {
        return storeWarehouseIds;
    }

    public Map<String, Object> getMeta() {
        return meta;
    }

    public Map<String, Boolean> getChecks() {
        return checks;
    }

    public void check(String name, boolean ok) {
        checks.put(name, ok);
    }
}
