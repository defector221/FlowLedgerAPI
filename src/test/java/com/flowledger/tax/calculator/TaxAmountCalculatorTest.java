package com.flowledger.tax.calculator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.flowledger.tax.config.TaxEngineProperties;
import com.flowledger.tax.domain.PricingMode;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TaxAmountCalculatorTest {
    private final TaxAmountCalculator calculator = new TaxAmountCalculator(new TaxEngineProperties());

    @Test
    void exclusiveTaxOnTaxableBase() {
        BigDecimal taxable =
                calculator.taxableFromGross(new BigDecimal("100"), new BigDecimal("18"), PricingMode.EXCLUSIVE);
        assertEquals(new BigDecimal("100.00"), taxable);
        assertEquals(new BigDecimal("18.00"), calculator.taxFromTaxable(taxable, new BigDecimal("18")));
        assertEquals(
                new BigDecimal("118.00"),
                calculator.lineTotal(taxable, new BigDecimal("18"), PricingMode.EXCLUSIVE, new BigDecimal("100")));
    }

    @Test
    void inclusiveExtractsNetFromGross() {
        BigDecimal gross = new BigDecimal("118");
        BigDecimal taxable = calculator.taxableFromGross(gross, new BigDecimal("18"), PricingMode.INCLUSIVE);
        assertEquals(new BigDecimal("100.00"), taxable);
        assertEquals(
                new BigDecimal("118.00"),
                calculator.lineTotal(taxable, new BigDecimal("18"), PricingMode.INCLUSIVE, gross));
    }

    @Test
    void zeroRateProducesZeroTax() {
        assertEquals(new BigDecimal("0.00"), calculator.taxFromTaxable(new BigDecimal("500"), BigDecimal.ZERO));
    }

    @Test
    void grossAmountSubtractsDiscount() {
        assertEquals(
                0,
                new BigDecimal("900.00")
                        .compareTo(calculator.grossAmount(
                                new BigDecimal("10"), new BigDecimal("100"), new BigDecimal("100"))));
    }
}
