package com.flowledger.demo.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flowledger.demo.catalog.CatalogStrategyId;
import com.flowledger.demo.workflow.WorkflowPack;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class DemoScenarioRegistryTest {
    private final DemoScenarioRegistry registry = new DemoScenarioRegistry();

    @Test
    void listsNineScenarios() {
        assertEquals(9, registry.all().size());
        assertEquals(EnumSet.allOf(DemoScenario.class), EnumSet.copyOf(registry.all()));
    }

    @ParameterizedTest
    @EnumSource(DemoScenario.class)
    void everyScenarioHasCompleteBlueprint(DemoScenario scenario) {
        DemoBlueprint bp = registry.require(scenario);
        assertEquals(scenario, bp.scenario());
        assertEquals(scenario.organizationName(), bp.organizationName());
        assertTrue(bp.branchCount() >= 1, scenario + " branches");
        assertTrue(bp.storeCount() >= 1, scenario + " stores");
        assertTrue(bp.productCount() >= 1, scenario + " products");
        assertTrue(bp.customerCount() >= 1, scenario + " customers");
        assertTrue(bp.supplierCount() >= 1, scenario + " suppliers");
        assertTrue(bp.salesCount() >= 0, scenario + " sales");
        assertTrue(bp.purchaseCount() >= 0, scenario + " purchases");
        assertNotNull(bp.catalogStrategy(), scenario + " catalog");
        assertNotNull(bp.workflow(), scenario + " workflow");
        assertTrue(bp.estimatedMinutes() >= 1, scenario + " estimate");
        assertFalse(scenario.command().isBlank());
        assertEquals(scenario, DemoScenario.fromCommand(scenario.command()));
    }

    @Test
    void catalogStrategiesCoverAllBlueprints() {
        Set<CatalogStrategyId> used = EnumSet.noneOf(CatalogStrategyId.class);
        for (DemoScenario s : DemoScenario.values()) {
            used.add(registry.require(s).catalogStrategy());
        }
        assertTrue(used.contains(CatalogStrategyId.MIXED_RETAIL));
        assertTrue(used.contains(CatalogStrategyId.GROCERY));
        assertTrue(used.contains(CatalogStrategyId.FASHION));
        assertTrue(used.contains(CatalogStrategyId.ELECTRONICS));
        assertTrue(used.contains(CatalogStrategyId.PHARMACY));
        assertTrue(used.contains(CatalogStrategyId.WHOLESALE));
    }

    @Test
    void workflowFlagsPerVertical() {
        assertTrue(registry.require(DemoScenario.RETAIL_SMALL).workflow().posHeavy());
        assertTrue(registry.require(DemoScenario.GROCERY_CHAIN).workflow().batchExpiryHeavy());
        assertTrue(registry.require(DemoScenario.FASHION_CHAIN).workflow().fashionVariants());
        assertTrue(registry.require(DemoScenario.ELECTRONICS_CHAIN).workflow().serialTrackingHeavy());
        assertTrue(registry.require(DemoScenario.PHARMACY).workflow().batchExpiryHeavy());
        assertTrue(registry.require(DemoScenario.WHOLESALE).workflow().creditSalesHeavy());
        assertTrue(registry.require(DemoScenario.WHOLESALE).workflow().largePurchaseOrders());
        assertFalse(registry.require(DemoScenario.WHOLESALE).workflow().posHeavy());
        assertTrue(registry.require(DemoScenario.OMNI_CHANNEL).workflow().onlineStore());
    }

    @Test
    void retailSmallDefaults() {
        DemoBlueprint bp = registry.require(DemoScenario.RETAIL_SMALL);
        assertEquals("Retail World (Small)", bp.organizationName());
        assertEquals(1, bp.branchCount());
        assertEquals(2, bp.storeCount());
        assertEquals(500, bp.productCount());
        assertEquals(WorkflowPack.retailDefault(), bp.workflow());
    }

    @Test
    void overridesShrinkEnterprise() {
        DemoBlueprint bp = registry.require(DemoScenario.RETAIL_ENTERPRISE).withOverrides(Map.of("productCount", 100));
        assertEquals(100, bp.productCount());
        assertEquals(100, bp.branchCount());
    }

    @Test
    void fromCommandAliases() {
        assertEquals(DemoScenario.GROCERY_CHAIN, DemoScenario.fromCommand("grocery-chain"));
        assertEquals(DemoScenario.OMNI_CHANNEL, DemoScenario.fromCommand("omni-channel"));
        assertEquals(DemoScenario.RETAIL_SMALL, DemoScenario.fromCommand(null));
    }
}
