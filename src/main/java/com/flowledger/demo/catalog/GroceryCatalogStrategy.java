package com.flowledger.demo.catalog;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class GroceryCatalogStrategy implements CatalogStrategy {
    @Override
    public CatalogStrategyId id() {
        return CatalogStrategyId.GROCERY;
    }

    @Override
    public List<String> categoryNames() {
        return List.of(
                "Rice",
                "Wheat",
                "Oil",
                "Milk",
                "Bread",
                "Eggs",
                "Tea",
                "Coffee",
                "Biscuits",
                "Chocolate",
                "Soft Drinks",
                "Juice",
                "Snacks",
                "Frozen Foods",
                "Cleaning",
                "Personal Care",
                "Soap",
                "Shampoo",
                "Detergent",
                "Vegetables",
                "Fruits",
                "Spices");
    }

    @Override
    public List<String> brandNames() {
        return List.of("FreshMart", "GreenBasket", "DailyDairy", "SpiceKing", "CleanHome");
    }

    @Override
    public List<String> productNameSeeds() {
        return categoryNames();
    }

    @Override
    public boolean preferBatchExpiry() {
        return true;
    }

    @Override
    public boolean preferSerial() {
        return false;
    }

    @Override
    public boolean preferVariants() {
        return false;
    }
}
