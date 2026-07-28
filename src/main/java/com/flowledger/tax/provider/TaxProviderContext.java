package com.flowledger.tax.provider;

import com.flowledger.tax.domain.TaxProviderCode;
import com.flowledger.tax.entity.OrganizationTaxSettings;
import com.flowledger.tax.entity.TaxCategory;
import com.flowledger.tax.entity.TaxRule;
import java.util.Optional;

public record TaxProviderContext(
        TaxProviderCode providerCode, OrganizationTaxSettings settings, TaxCategory category, TaxRule rule) {

    public static TaxProviderContext of(
            TaxProviderCode providerCode, OrganizationTaxSettings settings, TaxCategory category, TaxRule rule) {
        return new TaxProviderContext(providerCode, settings, category, rule);
    }

    public Optional<OrganizationTaxSettings> settingsOpt() {
        return Optional.ofNullable(settings);
    }
}
