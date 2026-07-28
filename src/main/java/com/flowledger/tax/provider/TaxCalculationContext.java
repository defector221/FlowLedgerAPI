package com.flowledger.tax.provider;

import com.flowledger.product.entity.SplitStrategy;
import com.flowledger.product.entity.TaxType;
import com.flowledger.tax.domain.PricingMode;
import java.math.BigDecimal;

public record TaxCalculationContext(
        TaxProviderContext providerContext,
        String organizationStateCode,
        String placeOfSupplyStateCode,
        BigDecimal quantity,
        BigDecimal rate,
        BigDecimal discount,
        PricingMode pricingMode,
        TaxType taxType,
        SplitStrategy splitStrategy,
        BigDecimal cgstSharePercent,
        BigDecimal sgstSharePercent,
        boolean interState,
        boolean intraState) {}
