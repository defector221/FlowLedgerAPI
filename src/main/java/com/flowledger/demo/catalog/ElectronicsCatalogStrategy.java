package com.flowledger.demo.catalog;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ElectronicsCatalogStrategy implements CatalogStrategy {
    @Override
    public CatalogStrategyId id() {
        return CatalogStrategyId.ELECTRONICS;
    }

    @Override
    public List<String> categoryNames() {
        return List.of(
                "TV",
                "Refrigerator",
                "AC",
                "Mixer",
                "Washing Machine",
                "Microwave",
                "Fan",
                "Iron",
                "Water Purifier",
                "Laptop",
                "Desktop",
                "Monitor",
                "Keyboard",
                "Mouse",
                "SSD",
                "Pendrive",
                "Tablet",
                "Printer",
                "Router",
                "Camera",
                "Headphones",
                "Phone");
    }

    @Override
    public List<String> brandNames() {
        return List.of("VoltTech", "CoolAir", "ByteHome", "VisionPlus", "SoundMax");
    }

    @Override
    public List<String> productNameSeeds() {
        return categoryNames();
    }

    @Override
    public boolean preferBatchExpiry() {
        return false;
    }

    @Override
    public boolean preferSerial() {
        return true;
    }

    @Override
    public boolean preferVariants() {
        return false;
    }
}
