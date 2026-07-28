package com.flowledger.tax.provider;

import com.flowledger.product.entity.SplitStrategy;
import com.flowledger.product.entity.TaxType;
import com.flowledger.tax.calculator.TaxAmountCalculator;
import com.flowledger.tax.domain.PricingMode;
import com.flowledger.tax.domain.TaxProviderCode;
import com.flowledger.tax.dto.TaxCalculationDtos.TaxBreakdown;
import com.flowledger.tax.dto.TaxCalculationDtos.TaxBreakdownLine;
import com.flowledger.tax.dto.TaxCalculationDtos.TaxResult;
import com.flowledger.tax.entity.TaxCategory;
import com.flowledger.tax.entity.TaxRule;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class IndiaGSTProvider implements TaxProvider {
    private static final BigDecimal FIFTY = new BigDecimal("50");

    private final TaxAmountCalculator calculator;

    public IndiaGSTProvider(TaxAmountCalculator calculator) {
        this.calculator = calculator;
    }

    @Override
    public TaxProviderCode code() {
        return TaxProviderCode.IndiaGST;
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
        BigDecimal totalRate = totalRate(rule);
        BigDecimal taxable = calculator.taxableFromGross(gross, totalRate, pricingMode);

        BigDecimal cgst = BigDecimal.ZERO;
        BigDecimal sgst = BigDecimal.ZERO;
        BigDecimal igst = BigDecimal.ZERO;
        List<TaxBreakdownLine> lines = new ArrayList<>();

        SplitStrategy strategy = resolveStrategy(context);
        BigDecimal baseTax = calculator.taxFromTaxable(taxable, totalRate);

        switch (strategy) {
            case NO_SPLIT_IGST -> {
                igst = baseTax;
                lines.add(line("IGST", rule.getIgstRate(), igst));
            }
            case NO_SPLIT_OTHER -> {
                lines.add(line("OTHER", totalRate, baseTax));
            }
            case CUSTOM_PERCENT -> {
                var split = splitByShares(baseTax, context.cgstSharePercent(), context.sgstSharePercent());
                cgst = split.cgst();
                sgst = split.sgst();
                lines.add(line("CGST", rule.getCgstRate(), cgst));
                lines.add(line("SGST", rule.getSgstRate(), sgst));
            }
            case PLACE_OF_SUPPLY -> {
                if (context.interState()) {
                    igst = baseTax;
                    lines.add(line("IGST", rule.getIgstRate(), igst));
                } else {
                    var split = splitByShares(baseTax, context.cgstSharePercent(), context.sgstSharePercent());
                    cgst = split.cgst();
                    sgst = split.sgst();
                    lines.add(line("CGST", rule.getCgstRate(), cgst));
                    lines.add(line("SGST", rule.getSgstRate(), sgst));
                }
            }
        }

        BigDecimal cess = calculator.taxFromTaxable(taxable, rule.getCessRate());
        if (cess.signum() > 0) {
            lines.add(line("CESS", rule.getCessRate(), cess));
        }

        BigDecimal otherTax = strategy == SplitStrategy.NO_SPLIT_OTHER ? baseTax : BigDecimal.ZERO;
        BigDecimal totalTax = cgst.add(sgst).add(igst).add(cess).add(otherTax);
        BigDecimal lineTotal = calculator.lineTotal(taxable, totalTax, pricingMode, gross);

        return new TaxResult(
                category == null ? null : category.getId(),
                category == null ? null : category.getCode(),
                rule.getId(),
                rule.getVersionLabel(),
                taxable,
                cgst,
                sgst,
                igst,
                cess,
                otherTax,
                lineTotal,
                new TaxBreakdown(taxable, totalTax, cess, List.copyOf(lines)),
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

    private static BigDecimal totalRate(TaxRule rule) {
        if (rule.getIgstRate() != null && rule.getIgstRate().signum() > 0) {
            return rule.getIgstRate();
        }
        BigDecimal cgst = rule.getCgstRate() == null ? BigDecimal.ZERO : rule.getCgstRate();
        BigDecimal sgst = rule.getSgstRate() == null ? BigDecimal.ZERO : rule.getSgstRate();
        return cgst.add(sgst);
    }

    private SplitStrategy resolveStrategy(TaxCalculationContext context) {
        if (context.splitStrategy() != null) {
            return context.splitStrategy();
        }
        TaxType type = context.taxType() == null ? TaxType.GST : context.taxType();
        return SplitStrategy.defaultFor(type);
    }

    private static TaxBreakdownLine line(String component, BigDecimal rate, BigDecimal amount) {
        return new TaxBreakdownLine(component, rate, amount);
    }

    private ShareSplit splitByShares(BigDecimal tax, BigDecimal cgstShare, BigDecimal sgstShare) {
        BigDecimal cgstPct = cgstShare == null ? FIFTY : cgstShare;
        BigDecimal cgst = calculator.scale(
                tax.multiply(cgstPct).divide(BigDecimal.valueOf(100), calculator.scale(), calculator.roundingMode()));
        BigDecimal sgst = calculator.scale(tax.subtract(cgst));
        if (sgstShare != null && cgstShare != null) {
            BigDecimal expectedSgst = calculator.scale(tax.multiply(sgstShare)
                    .divide(BigDecimal.valueOf(100), calculator.scale(), calculator.roundingMode()));
            if (expectedSgst.subtract(sgst).abs().compareTo(new BigDecimal("0.02")) <= 0) {
                sgst = calculator.scale(tax.subtract(cgst));
            }
        }
        return new ShareSplit(cgst, sgst);
    }

    private record ShareSplit(BigDecimal cgst, BigDecimal sgst) {}
}
