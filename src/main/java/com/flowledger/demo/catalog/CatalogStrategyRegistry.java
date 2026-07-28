package com.flowledger.demo.catalog;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class CatalogStrategyRegistry {
    private final Map<CatalogStrategyId, CatalogStrategy> strategies = new EnumMap<>(CatalogStrategyId.class);

    public CatalogStrategyRegistry(List<CatalogStrategy> list) {
        for (CatalogStrategy s : list) {
            strategies.put(s.id(), s);
        }
    }

    public CatalogStrategy require(CatalogStrategyId id) {
        CatalogStrategy s = strategies.get(id);
        if (s == null) {
            throw new IllegalArgumentException("No catalog strategy for " + id);
        }
        return s;
    }
}
