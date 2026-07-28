package com.flowledger.demo.catalog;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class MixedRetailCatalogStrategy implements CatalogStrategy {
    @Override
    public CatalogStrategyId id() {
        return CatalogStrategyId.MIXED_RETAIL;
    }

    @Override
    public List<String> categoryNames() {
        return List.of(
                "Grocery",
                "Dairy",
                "Beverages",
                "Snacks",
                "Personal Care",
                "Fashion Men",
                "Fashion Women",
                "Electronics",
                "Digital",
                "Home");
    }

    @Override
    public List<String> brandNames() {
        return List.of("FreshFarm", "DailyCare", "StyleCo", "VoltTech", "ByteHome", "MediPlus");
    }

    @Override
    public List<String> productNameSeeds() {
        return List.of(
                "Basmati Rice",
                "Wheat Flour",
                "Sunflower Oil",
                "Toned Milk",
                "Whole Wheat Bread",
                "Farm Eggs",
                "Assam Tea",
                "Filter Coffee",
                "Glucose Biscuits",
                "Dark Chocolate",
                "Cola Soft Drink",
                "Mango Juice",
                "Potato Chips",
                "Frozen Peas",
                "Floor Cleaner",
                "Handwash",
                "Bath Soap",
                "Hair Shampoo",
                "Detergent Powder",
                "Tomatoes",
                "Bananas",
                "Turmeric Powder",
                "Cotton Shirt",
                "Denim Jeans",
                "LED TV",
                "Mixer Grinder",
                "Laptop",
                "Wireless Mouse",
                "SSD Drive",
                "Bluetooth Headphones");
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
        return false;
    }
}
