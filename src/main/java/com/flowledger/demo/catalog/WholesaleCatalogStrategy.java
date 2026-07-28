package com.flowledger.demo.catalog;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class WholesaleCatalogStrategy implements CatalogStrategy {
    @Override
    public CatalogStrategyId id() {
        return CatalogStrategyId.WHOLESALE;
    }

    @Override
    public List<String> categoryNames() {
        return List.of("Bulk Grocery", "Bulk Beverages", "Bulk FMCG", "Bulk Packaging", "Trade Packs");
    }

    @Override
    public List<String> brandNames() {
        return List.of("TradeBulk", "CartonKing", "WholesalePro");
    }

    @Override
    public List<String> productNameSeeds() {
        return List.of("Carton Rice 25kg", "Oil Tin 15L", "Soap Case", "Biscuit Outer", "Tea Chest");
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
