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

class EuropeVATProviderTest {
    private final EuropeVATProvider provider =
            new EuropeVATProvider(new TaxAmountCalculator(new TaxEngineProperties()));

    @Test
    void appliesSingleVatComponent() {
        TaxCategory category = new TaxCategory();
        category.setId(UUID.randomUUID());
        category.setCode("VAT_STANDARD");
        TaxRule rule = new TaxRule();
        rule.setId(UUID.randomUUID());
        rule.setIgstRate(new BigDecimal("20"));
        rule.setCgstRate(BigDecimal.ZERO);
        rule.setSgstRate(BigDecimal.ZERO);
        var result = provider.calculate(new TaxCalculationContext(
                TaxProviderContext.of(TaxProviderCode.EuropeVAT, null, category, rule),
                "DE",
                "DE",
                BigDecimal.ONE,
                new BigDecimal("100"),
                BigDecimal.ZERO,
                PricingMode.EXCLUSIVE,
                TaxType.OTHER,
                null,
                null,
                null,
                false,
                true));
        assertEquals(new BigDecimal("20.00"), result.igstAmount());
        assertEquals(new BigDecimal("120.00"), result.lineTotal());
    }
}
