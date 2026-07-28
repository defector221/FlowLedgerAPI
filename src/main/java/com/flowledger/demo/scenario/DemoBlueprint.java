package com.flowledger.demo.scenario;

import com.flowledger.demo.catalog.CatalogStrategyId;
import com.flowledger.demo.workflow.WorkflowPack;
import java.util.Map;

/** Immutable recipe for a named demo scenario. */
public record DemoBlueprint(
        DemoScenario scenario,
        String organizationName,
        int branchCount,
        int storeCount,
        int warehousesPerBranch,
        int terminalsPerStore,
        int productCount,
        int customerCount,
        int supplierCount,
        int salesCount,
        int purchaseCount,
        CatalogStrategyId catalogStrategy,
        WorkflowPack workflow,
        int estimatedMinutes) {

    public DemoBlueprint withOverrides(Map<String, Integer> overrides) {
        if (overrides == null || overrides.isEmpty()) {
            return this;
        }
        return new DemoBlueprint(
                scenario,
                organizationName,
                override(overrides, "branchCount", branchCount),
                override(overrides, "storeCount", storeCount),
                override(overrides, "warehousesPerBranch", warehousesPerBranch),
                override(overrides, "terminalsPerStore", terminalsPerStore),
                override(overrides, "productCount", productCount),
                override(overrides, "customerCount", customerCount),
                override(overrides, "supplierCount", supplierCount),
                override(overrides, "salesCount", salesCount),
                override(overrides, "purchaseCount", purchaseCount),
                catalogStrategy,
                workflow,
                estimatedMinutes);
    }

    private static int override(Map<String, Integer> map, String key, int fallback) {
        Integer v = map.get(key);
        return v == null || v < 0 ? fallback : v;
    }
}
