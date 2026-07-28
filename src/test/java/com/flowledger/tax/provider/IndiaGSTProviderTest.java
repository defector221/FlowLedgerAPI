package com.flowledger.tax.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flowledger.product.entity.SplitStrategy;
import com.flowledger.product.entity.TaxType;
import com.flowledger.tax.calculator.TaxAmountCalculator;
import com.flowledger.tax.config.TaxEngineProperties;
import com.flowledger.tax.domain.PricingMode;
import com.flowledger.tax.domain.TaxProviderCode;
import com.flowledger.tax.dto.TaxCalculationDtos.TaxResult;
import com.flowledger.tax.entity.TaxCategory;
import com.flowledger.tax.entity.TaxRule;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class IndiaGSTProviderTest {
    private final IndiaGSTProvider provider = new IndiaGSTProvider(new TaxAmountCalculator(new TaxEngineProperties()));

    @Test
    void intraStateSplitsCgstSgst() {
        TaxResult result = provider.calculate(context("27", "27", rule(false, false, false), false, true));
        assertEquals(new BigDecimal("9.00"), result.cgstAmount());
        assertEquals(new BigDecimal("9.00"), result.sgstAmount());
        assertEquals(new BigDecimal("0"), result.igstAmount());
        assertTrue(result.intraState());
    }

    @Test
    void interStateUsesIgst() {
        TaxResult result = provider.calculate(context("27", "29", rule(false, false, false), true, false));
        assertEquals(new BigDecimal("0"), result.cgstAmount());
        assertEquals(new BigDecimal("0"), result.sgstAmount());
        assertEquals(new BigDecimal("18.00"), result.igstAmount());
        assertTrue(result.interState());
    }

    @Test
    void exemptProducesZeroTax() {
        TaxRule rule = rule(false, true, false);
        TaxResult result = provider.calculate(context("27", "27", rule, false, true));
        assertEquals(new BigDecimal("100.00"), result.taxableAmount());
        assertEquals(new BigDecimal("0"), result.cgstAmount());
        assertEquals(new BigDecimal("100.00"), result.lineTotal());
    }

    @Test
    void zeroRatedProducesZeroTax() {
        TaxRule rule = rule(true, false, false);
        TaxResult result = provider.calculate(context("27", "27", rule, false, true));
        assertEquals(new BigDecimal("0"), result.cgstAmount());
        assertEquals(new BigDecimal("100.00"), result.lineTotal());
    }

    @Test
    void nilRatedProducesZeroTax() {
        TaxRule rule = rule(false, false, true);
        TaxResult result = provider.calculate(context("27", "27", rule, false, true));
        assertEquals(new BigDecimal("0"), result.igstAmount());
    }

    private static TaxCalculationContext context(
            String orgState, String pos, TaxRule rule, boolean inter, boolean intra) {
        TaxCategory category = new TaxCategory();
        category.setId(UUID.randomUUID());
        category.setCode("GST_18");
        return new TaxCalculationContext(
                TaxProviderContext.of(TaxProviderCode.IndiaGST, null, category, rule),
                orgState,
                pos,
                BigDecimal.ONE,
                new BigDecimal("100"),
                BigDecimal.ZERO,
                PricingMode.EXCLUSIVE,
                TaxType.GST,
                SplitStrategy.PLACE_OF_SUPPLY,
                new BigDecimal("50"),
                new BigDecimal("50"),
                inter,
                intra);
    }

    private static TaxRule rule(boolean zeroRated, boolean exempt, boolean nilRated) {
        TaxRule rule = new TaxRule();
        rule.setId(UUID.randomUUID());
        rule.setVersionLabel("v1");
        rule.setCgstRate(new BigDecimal("9"));
        rule.setSgstRate(new BigDecimal("9"));
        rule.setIgstRate(new BigDecimal("18"));
        rule.setCessRate(BigDecimal.ZERO);
        rule.setZeroRated(zeroRated);
        rule.setExempt(exempt);
        rule.setNilRated(nilRated);
        return rule;
    }
}
