package com.flowledger.tax.calculator;

import com.flowledger.tax.config.TaxEngineProperties;
import com.flowledger.tax.domain.PricingMode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

@Component
public class TaxAmountCalculator {
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final TaxEngineProperties properties;

    public TaxAmountCalculator(TaxEngineProperties properties) {
        this.properties = properties;
    }

    public BigDecimal grossAmount(BigDecimal quantity, BigDecimal rate, BigDecimal discount) {
        BigDecimal qty = quantity == null ? BigDecimal.ZERO : quantity;
        BigDecimal unitRate = rate == null ? BigDecimal.ZERO : rate;
        BigDecimal disc = discount == null ? BigDecimal.ZERO : discount;
        return qty.multiply(unitRate).subtract(disc);
    }

    public BigDecimal taxableFromGross(BigDecimal gross, BigDecimal totalRatePercent, PricingMode pricingMode) {
        if (gross == null) {
            return zero();
        }
        if (pricingMode == PricingMode.INCLUSIVE) {
            BigDecimal divisor = HUNDRED.add(totalRatePercent == null ? BigDecimal.ZERO : totalRatePercent);
            if (divisor.signum() == 0) {
                return scale(gross);
            }
            return scale(gross.multiply(HUNDRED).divide(divisor, scale(), roundingMode()));
        }
        return scale(gross);
    }

    public BigDecimal taxFromTaxable(BigDecimal taxable, BigDecimal ratePercent) {
        if (taxable == null || ratePercent == null || ratePercent.signum() == 0) {
            return zero();
        }
        return scale(taxable.multiply(ratePercent).divide(HUNDRED, scale(), roundingMode()));
    }

    public BigDecimal lineTotal(BigDecimal taxable, BigDecimal totalTax, PricingMode pricingMode, BigDecimal gross) {
        if (pricingMode == PricingMode.INCLUSIVE) {
            return scale(gross == null ? BigDecimal.ZERO : gross);
        }
        BigDecimal base = taxable == null ? BigDecimal.ZERO : taxable;
        BigDecimal tax = totalTax == null ? BigDecimal.ZERO : totalTax;
        return scale(base.add(tax));
    }

    public BigDecimal scale(BigDecimal value) {
        if (value == null) {
            return zero();
        }
        return value.setScale(scale(), roundingMode());
    }

    public int scale() {
        return properties.getRoundingScale();
    }

    public RoundingMode roundingMode() {
        try {
            return RoundingMode.valueOf(properties.getRoundingMode());
        } catch (IllegalArgumentException ex) {
            return RoundingMode.HALF_UP;
        }
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(scale(), roundingMode());
    }
}
