package com.flowledger.tax.strategy;

import com.flowledger.tax.domain.TaxProviderCode;
import com.flowledger.tax.provider.TaxProvider;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class TaxProviderRegistry {
    private final Map<TaxProviderCode, TaxProvider> providers;

    public TaxProviderRegistry(List<TaxProvider> providerList) {
        this.providers = providerList.stream().collect(Collectors.toMap(TaxProvider::code, Function.identity()));
    }

    public TaxProvider resolve(TaxProviderCode code) {
        TaxProviderCode resolved = code == null ? TaxProviderCode.IndiaGST : code;
        TaxProvider provider = providers.get(resolved);
        if (provider == null) {
            provider = providers.get(TaxProviderCode.IndiaGST);
        }
        if (provider == null) {
            throw new IllegalStateException("No tax provider registered for " + resolved);
        }
        return provider;
    }
}
