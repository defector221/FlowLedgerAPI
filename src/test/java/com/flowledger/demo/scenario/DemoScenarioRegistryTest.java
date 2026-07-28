package com.flowledger.demo.scenario;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class DemoScenarioRegistryTest {
    private final DemoScenarioRegistry registry = new DemoScenarioRegistry();

    @Test
    void listsNineScenarios() {
        assertEquals(9, registry.all().size());
    }

    @Test
    void retailSmallDefaults() {
        DemoBlueprint bp = registry.require(DemoScenario.RETAIL_SMALL);
        assertEquals("Retail World (Small)", bp.organizationName());
        assertEquals(1, bp.branchCount());
        assertEquals(2, bp.storeCount());
        assertEquals(500, bp.productCount());
    }

    @Test
    void fashionAndPharmacyVerticals() {
        assertEquals(
                com.flowledger.demo.catalog.CatalogStrategyId.FASHION,
                registry.require(DemoScenario.FASHION_CHAIN).catalogStrategy());
        assertTrue(registry.require(DemoScenario.FASHION_CHAIN).workflow().fashionVariants());
        assertTrue(registry.require(DemoScenario.PHARMACY).workflow().batchExpiryHeavy());
        assertTrue(registry.require(DemoScenario.WHOLESALE).workflow().creditSalesHeavy());
        assertTrue(registry.require(DemoScenario.OMNI_CHANNEL).workflow().onlineStore());
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
        assertEquals(DemoScenario.RETAIL_SMALL, DemoScenario.fromCommand(null));
    }
}
