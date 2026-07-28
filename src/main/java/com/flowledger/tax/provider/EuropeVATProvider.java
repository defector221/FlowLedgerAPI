package com.flowledger.tax.provider;

import com.flowledger.tax.calculator.TaxAmountCalculator;
import com.flowledger.tax.domain.PricingMode;
import com.flowledger.tax.domain.TaxProviderCode;
import com.flowledger.tax.dto.TaxCalculationDtos.TaxBreakdown;
import com.flowledger.tax.dto.TaxCalculationDtos.TaxBreakdownLine;
import com.flowledger.tax.dto.TaxCalculationDtos.TaxResult;
import com.flowledger.tax.entity.TaxCategory;
import com.flowledger.tax.entity.TaxRule;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class EuropeVATProvider implements TaxProvider {
    private final TaxAmountCalculator calculator;

    public EuropeVATProvider(TaxAmountCalculator calculator) {
        this.calculator = calculator;
    }

    @Override
    public TaxProviderCode code() {
        return TaxProviderCode.EuropeVAT;
    }

    @Override
    public TaxResult calculate(TaxCalculationContext context) {
        TaxRule rule = context.providerContext().rule();
        TaxCategory category = context.providerContext().category();
        if (rule.isExempt() || rule.isNilRated() || rule.isZeroRated()) {
            return zeroResult(context, category, rule);
        }

        BigDecimal gross = calculator.grossAmount(context.quantity(), context.rate(), context.discount());
        PricingMode pricingMode = rule.isInclusive() ? PricingMode.INCLUSIVE : context.pricingMode();
        BigDecimal vatRate = rule.getIgstRate() != null && rule.getIgstRate().signum() > 0
                ? rule.getIgstRate()
                : rule.getCgstRate().add(rule.getSgstRate());
        BigDecimal taxable = calculator.taxableFromGross(gross, vatRate, pricingMode);
        BigDecimal vat = calculator.taxFromTaxable(taxable, vatRate);
        BigDecimal lineTotal = calculator.lineTotal(taxable, vat, pricingMode, gross);

        return new TaxResult(
                category == null ? null : category.getId(),
                category == null ? null : category.getCode(),
                rule.getId(),
                rule.getVersionLabel(),
                taxable,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                vat,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                lineTotal,
                new TaxBreakdown(taxable, vat, BigDecimal.ZERO, List.of(new TaxBreakdownLine("VAT", vatRate, vat))),
                context.interState(),
                context.intraState());
    }

    private TaxResult zeroResult(TaxCalculationContext context, TaxCategory category, TaxRule rule) {
        BigDecimal gross = calculator.grossAmount(context.quantity(), context.rate(), context.discount());
        BigDecimal taxable = calculator.scale(gross);
        return new TaxResult(
                category == null ? null : category.getId(),
                category == null ? null : category.getCode(),
                rule.getId(),
                rule.getVersionLabel(),
                taxable,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                taxable,
                new TaxBreakdown(taxable, BigDecimal.ZERO, BigDecimal.ZERO, List.of()),
                context.interState(),
                context.intraState());
    }
}
