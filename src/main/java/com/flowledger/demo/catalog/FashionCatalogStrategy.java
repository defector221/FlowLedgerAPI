package com.flowledger.demo.catalog;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FashionCatalogStrategy implements CatalogStrategy {
    @Override
    public CatalogStrategyId id() {
        return CatalogStrategyId.FASHION;
    }

    @Override
    public List<String> categoryNames() {
        return List.of(
                "Men",
                "Women",
                "Kids",
                "Shoes",
                "Sandals",
                "Shirts",
                "T-Shirts",
                "Jeans",
                "Jackets",
                "Accessories",
                "Belts",
                "Wallets",
                "Bags",
                "Watches");
    }

    @Override
    public List<String> brandNames() {
        return List.of("UrbanThread", "StyleHub", "DenimWorks", "WalkEasy", "LuxeBag");
    }

    @Override
    public List<String> productNameSeeds() {
        return List.of("Classic Shirt", "Slim Jeans", "Crew Tee", "Running Shoe", "Leather Belt", "Canvas Bag");
    }

    @Override
    public boolean preferBatchExpiry() {
        return false;
    }

    @Override
    public boolean preferSerial() {
        return false;
    }

    @Override
    public boolean preferVariants() {
        return true;
    }
}
