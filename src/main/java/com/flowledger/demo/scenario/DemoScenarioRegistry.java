package com.flowledger.demo.scenario;

import com.flowledger.demo.catalog.CatalogStrategyId;
import com.flowledger.demo.workflow.WorkflowPack;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DemoScenarioRegistry {
    private final Map<DemoScenario, DemoBlueprint> blueprints = new EnumMap<>(DemoScenario.class);

    public DemoScenarioRegistry() {
        blueprints.put(
                DemoScenario.RETAIL_SMALL,
                bp(
                        DemoScenario.RETAIL_SMALL,
                        1,
                        2,
                        500,
                        100,
                        20,
                        300,
                        30,
                        CatalogStrategyId.MIXED_RETAIL,
                        WorkflowPack.retailDefault(),
                        3));
        blueprints.put(
                DemoScenario.RETAIL_MEDIUM,
                bp(
                        DemoScenario.RETAIL_MEDIUM,
                        5,
                        20,
                        10_000,
                        1_000,
                        80,
                        8_000,
                        400,
                        CatalogStrategyId.MIXED_RETAIL,
                        WorkflowPack.retailDefault(),
                        25));
        blueprints.put(
                DemoScenario.RETAIL_ENTERPRISE,
                bp(
                        DemoScenario.RETAIL_ENTERPRISE,
                        100,
                        500,
                        100_000,
                        5_000,
                        200,
                        50_000,
                        2_000,
                        CatalogStrategyId.MIXED_RETAIL,
                        WorkflowPack.retailDefault(),
                        180));
        blueprints.put(
                DemoScenario.GROCERY_CHAIN,
                bp(
                        DemoScenario.GROCERY_CHAIN,
                        4,
                        12,
                        3_000,
                        400,
                        40,
                        5_000,
                        200,
                        CatalogStrategyId.GROCERY,
                        WorkflowPack.grocery(),
                        15));
        blueprints.put(
                DemoScenario.FASHION_CHAIN,
                bp(
                        DemoScenario.FASHION_CHAIN,
                        4,
                        12,
                        4_000,
                        400,
                        40,
                        4_000,
                        150,
                        CatalogStrategyId.FASHION,
                        WorkflowPack.fashion(),
                        15));
        blueprints.put(
                DemoScenario.ELECTRONICS_CHAIN,
                bp(
                        DemoScenario.ELECTRONICS_CHAIN,
                        4,
                        10,
                        2_500,
                        300,
                        35,
                        3_000,
                        120,
                        CatalogStrategyId.ELECTRONICS,
                        WorkflowPack.electronics(),
                        12));
        blueprints.put(
                DemoScenario.PHARMACY,
                bp(
                        DemoScenario.PHARMACY,
                        3,
                        9,
                        2_000,
                        300,
                        30,
                        3_000,
                        150,
                        CatalogStrategyId.PHARMACY,
                        WorkflowPack.pharmacy(),
                        12));
        blueprints.put(
                DemoScenario.WHOLESALE,
                bp(
                        DemoScenario.WHOLESALE,
                        2,
                        4,
                        2_000,
                        200,
                        50,
                        1_500,
                        300,
                        CatalogStrategyId.WHOLESALE,
                        WorkflowPack.wholesale(),
                        12));
        blueprints.put(
                DemoScenario.OMNI_CHANNEL,
                bp(
                        DemoScenario.OMNI_CHANNEL,
                        4,
                        11,
                        4_000,
                        500,
                        50,
                        5_000,
                        200,
                        CatalogStrategyId.MIXED_RETAIL,
                        WorkflowPack.omni(),
                        18));
    }

    private static DemoBlueprint bp(
            DemoScenario scenario,
            int branches,
            int stores,
            int products,
            int customers,
            int suppliers,
            int sales,
            int purchases,
            CatalogStrategyId catalog,
            WorkflowPack pack,
            int minutes) {
        return new DemoBlueprint(
                scenario,
                scenario.organizationName(),
                branches,
                stores,
                4,
                2,
                products,
                customers,
                suppliers,
                sales,
                purchases,
                catalog,
                pack,
                minutes);
    }

    public DemoBlueprint require(DemoScenario scenario) {
        DemoBlueprint b = blueprints.get(scenario);
        if (b == null) {
            throw new IllegalArgumentException("No blueprint for " + scenario);
        }
        return b;
    }

    public List<DemoScenario> all() {
        return Arrays.asList(DemoScenario.values());
    }
}
