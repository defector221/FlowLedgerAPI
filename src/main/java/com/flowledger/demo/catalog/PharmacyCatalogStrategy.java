package com.flowledger.demo.catalog;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class PharmacyCatalogStrategy implements CatalogStrategy {
    @Override
    public CatalogStrategyId id() {
        return CatalogStrategyId.PHARMACY;
    }

    @Override
    public List<String> categoryNames() {
        return List.of(
                "Tablets",
                "Syrups",
                "Ointments",
                "Vitamins",
                "First Aid",
                "Personal Hygiene",
                "OTC",
                "Rx Required");
    }

    @Override
    public List<String> brandNames() {
        return List.of("MediCare", "HealthPlus", "PharmaPure", "WellnessRx");
    }

    @Override
    public List<String> productNameSeeds() {
        return List.of(
                "Paracetamol 500mg",
                "Vitamin C",
                "Cough Syrup",
                "Antiseptic Cream",
                "ORS Sachet",
                "Bandage Roll",
                "Ibuprofen",
                "Multivitamin");
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
