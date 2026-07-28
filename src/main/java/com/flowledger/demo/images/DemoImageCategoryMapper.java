package com.flowledger.demo.images;

import com.flowledger.demo.catalog.CatalogStrategyId;
import com.flowledger.demo.scenario.DemoScenario;
import java.util.List;
import java.util.Locale;

/** Maps product name / catalog strategy to demo-images folder paths. */
public final class DemoImageCategoryMapper {
    private static final List<KeywordRule> KEYWORDS = List.of(
            new KeywordRule("air conditioner", "electronics/ac"),
            new KeywordRule("refrigerator", "electronics/refrigerator"),
            new KeywordRule("washing", "electronics/washing-machine"),
            new KeywordRule("microwave", "electronics/microwave"),
            new KeywordRule("headphone", "digital/headphones"),
            new KeywordRule("television", "electronics/tv"),
            new KeywordRule("sanitizer", "pharmacy/sanitizer"),
            new KeywordRule("ointment", "pharmacy/ointment"),
            new KeywordRule("medicine", "pharmacy/medicine"),
            new KeywordRule("syrup", "pharmacy/syrup"),
            new KeywordRule("iphone", "digital/phone"),
            new KeywordRule("laptop", "digital/laptop"),
            new KeywordRule("monitor", "digital/monitor"),
            new KeywordRule("keyboard", "digital/keyboard"),
            new KeywordRule("printer", "digital/printer"),
            new KeywordRule("router", "digital/router"),
            new KeywordRule("tablet", "digital/tablet"),
            new KeywordRule("camera", "digital/camera"),
            new KeywordRule("phone", "digital/phone"),
            new KeywordRule("mouse", "digital/mouse"),
            new KeywordRule("fridge", "electronics/refrigerator"),
            new KeywordRule("mixer", "electronics/mixer"),
            new KeywordRule("t-shirt", "fashion/tshirts"),
            new KeywordRule("shirt", "fashion/shirts"),
            new KeywordRule("jean", "fashion/jeans"),
            new KeywordRule("shoe", "fashion/shoes"),
            new KeywordRule("watch", "fashion/watches"),
            new KeywordRule("biscuit", "grocery/biscuit"),
            new KeywordRule("coffee", "grocery/coffee"),
            new KeywordRule("juice", "grocery/juice"),
            new KeywordRule("snack", "grocery/snacks"),
            new KeywordRule("wheat", "grocery/wheat"),
            new KeywordRule("milk", "grocery/milk"),
            new KeywordRule("rice", "grocery/rice"),
            new KeywordRule("tee", "fashion/tshirts"),
            new KeywordRule("bag", "fashion/bags"),
            new KeywordRule("oil", "grocery/oil"),
            new KeywordRule("tea", "grocery/tea"),
            new KeywordRule("tv", "electronics/tv"),
            new KeywordRule("ac ", "electronics/ac"));

    private DemoImageCategoryMapper() {}

    public static String resolve(String productName, CatalogStrategyId strategy, DemoScenario scenario) {
        String name = productName == null ? "" : productName.toLowerCase(Locale.ROOT);
        for (KeywordRule rule : KEYWORDS) {
            if (name.contains(rule.keyword())) {
                return rule.folder();
            }
        }
        return switch (strategy) {
            case GROCERY -> "grocery/snacks";
            case FASHION -> "fashion/shirts";
            case ELECTRONICS -> "electronics/tv";
            case PHARMACY -> "pharmacy/medicine";
            case WHOLESALE -> "grocery/oil";
            case MIXED_RETAIL ->
                switch (scenario) {
                    case GROCERY_CHAIN -> "grocery/snacks";
                    case FASHION_CHAIN -> "fashion/shirts";
                    case ELECTRONICS_CHAIN -> "digital/laptop";
                    case PHARMACY -> "pharmacy/medicine";
                    default -> "digital/phone";
                };
        };
    }

    private record KeywordRule(String keyword, String folder) {}
}
