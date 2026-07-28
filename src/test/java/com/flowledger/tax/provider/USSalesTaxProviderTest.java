package com.flowledger.tax.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.flowledger.product.entity.TaxType;
import com.flowledger.tax.calculator.TaxAmountCalculator;
import com.flowledger.tax.config.TaxEngineProperties;
import com.flowledger.tax.domain.PricingMode;
import com.flowledger.tax.domain.TaxProviderCode;
import com.flowledger.tax.entity.TaxCategory;
import com.flowledger.tax.entity.TaxRule;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class USSalesTaxProviderTest {
    private final USSalesTaxProvider provider =
            new USSalesTaxProvider(new TaxAmountCalculator(new TaxEngineProperties()));

    @Test
    void appliesSalesTaxComponent() {
        TaxCategory category = new TaxCategory();
        category.setId(UUID.randomUUID());
        category.setCode("SALES_TAX");
        TaxRule rule = new TaxRule();
        rule.setId(UUID.randomUUID());
        rule.setIgstRate(new BigDecimal("8.25"));
        var result = provider.calculate(new TaxCalculationContext(
                TaxProviderContext.of(TaxProviderCode.USSalesTax, null, category, rule),
                "TX",
                "TX",
                BigDecimal.ONE,
                new BigDecimal("200"),
                BigDecimal.ZERO,
                PricingMode.EXCLUSIVE,
                TaxType.OTHER,
                null,
                null,
                null,
                false,
                true));
        assertEquals(new BigDecimal("16.50"), result.otherTaxAmount());
        assertEquals(new BigDecimal("216.50"), result.lineTotal());
    }
}
