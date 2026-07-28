package com.flowledger.tax.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flowledger.tax.calculator.TaxAmountCalculator;
import com.flowledger.tax.config.TaxEngineProperties;
import com.flowledger.tax.dto.GstCalculationDtos.Request;
import com.flowledger.tax.provider.EuropeVATProvider;
import com.flowledger.tax.provider.IndiaGSTProvider;
import com.flowledger.tax.provider.USSalesTaxProvider;
import com.flowledger.tax.strategy.TaxProviderRegistry;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class TaxLineCalculatorTest {
    private final TaxLineCalculator calculator = new TaxLineCalculator(buildTaxService());

    private static TaxCalculationService buildTaxService() {
        TaxEngineProperties properties = new TaxEngineProperties();
        TaxAmountCalculator amountCalculator = new TaxAmountCalculator(properties);
        return new TaxCalculationService(
                null,
                null,
                null,
                new TaxProviderRegistry(List.of(
                        new IndiaGSTProvider(amountCalculator),
                        new EuropeVATProvider(amountCalculator),
                        new USSalesTaxProvider(amountCalculator))));
    }

    @Test
    void legacyRequestMapsToGstResponse() {
        var response = calculator.calculate(new Request(
                "29", "29", new BigDecimal("18"), false, BigDecimal.ONE, new BigDecimal("1000"), BigDecimal.ZERO));
        assertEquals(new BigDecimal("1000.00"), response.taxable());
        assertEquals(new BigDecimal("1180.00"), response.lineTotal());
    }

    @Test
    void documentLineFallsBackWhenNoEngineCategory() {
        var result = calculator.calculateDocumentLine(new TaxLineCalculator.DocumentLineInput(
                "29",
                "27",
                "IN",
                null,
                null,
                null,
                null,
                BigDecimal.ONE,
                new BigDecimal("100"),
                BigDecimal.ZERO,
                false,
                new BigDecimal("18"),
                "GST",
                "PLACE_OF_SUPPLY",
                new BigDecimal("50"),
                new BigDecimal("50")));
        assertEquals(new BigDecimal("18.00"), result.igstAmount());
        assertTrue(result.interState());
    }

    @Test
    void isInterStateDetectionViaService() {
        TaxCalculationService service = buildTaxService();
        assertTrue(service.isInterState("29", "27"));
        assertFalse(service.isIntraState("29", "27"));
    }
}
