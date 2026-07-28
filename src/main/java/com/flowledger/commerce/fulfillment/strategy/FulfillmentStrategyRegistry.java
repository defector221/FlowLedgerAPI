package com.flowledger.commerce.fulfillment.strategy;

import com.flowledger.commerce.fulfillment.FulfillmentType;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class FulfillmentStrategyRegistry {
    private final Map<FulfillmentType, FulfillmentStrategy> strategies;

    public FulfillmentStrategyRegistry(List<FulfillmentStrategy> strategyList) {
        this.strategies = strategyList.stream().collect(Collectors.toMap(FulfillmentStrategy::type, Function.identity()));
    }

    public FulfillmentStrategy require(FulfillmentType type) {
        FulfillmentStrategy strategy = strategies.get(type);
        if (strategy == null) {
            throw new IllegalStateException("No fulfillment strategy for " + type);
        }
        return strategy;
    }
}
