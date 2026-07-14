package com.flowledger.tax.service;

import com.flowledger.product.entity.SplitStrategy;
import com.flowledger.product.entity.TaxType;
import com.flowledger.tax.dto.GstCalculationDtos.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

@Service
public class GstCalculationService {
    private static final int SCALE = 2;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal FIFTY = new BigDecimal("50");

    public Response calculate(Request r) {
        BigDecimal gross =
                r.quantity().multiply(r.rate()).subtract(r.discount() == null ? BigDecimal.ZERO : r.discount());
        BigDecimal taxable = Boolean.TRUE.equals(r.taxInclusive())
                ? gross.multiply(HUNDRED).divide(HUNDRED.add(r.taxRate()), SCALE, RoundingMode.HALF_UP)
                : gross.setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal tax = taxable.multiply(r.taxRate()).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
        BigDecimal total =
                Boolean.TRUE.equals(r.taxInclusive()) ? gross.setScale(SCALE, RoundingMode.HALF_UP) : taxable.add(tax);

        SplitStrategy strategy = resolveStrategy(r);
        return switch (strategy) {
            case NO_SPLIT_IGST -> new Response(taxable, BigDecimal.ZERO, BigDecimal.ZERO, tax, BigDecimal.ZERO, total);
            case NO_SPLIT_OTHER -> new Response(taxable, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, tax, total);
            case CUSTOM_PERCENT -> {
                var split = splitByShares(tax, r.cgstSharePercent(), r.sgstSharePercent());
                yield new Response(taxable, split.cgst(), split.sgst(), BigDecimal.ZERO, BigDecimal.ZERO, total);
            }
            case PLACE_OF_SUPPLY -> {
                String orgState = normalizeState(r.organizationStateCode());
                String supplyState = normalizeState(r.placeOfSupplyStateCode());
                // Missing state codes → treat as intra-state (CGST/SGST) rather than failing the invoice.
                boolean intra = orgState.equalsIgnoreCase(supplyState);
                if (!intra) {
                    yield new Response(taxable, BigDecimal.ZERO, BigDecimal.ZERO, tax, BigDecimal.ZERO, total);
                }
                var split = splitByShares(tax, r.cgstSharePercent(), r.sgstSharePercent());
                yield new Response(taxable, split.cgst(), split.sgst(), BigDecimal.ZERO, BigDecimal.ZERO, total);
            }
        };
    }

    private static String normalizeState(String code) {
        return code == null ? "" : code.trim();
    }

    private static SplitStrategy resolveStrategy(Request r) {
        SplitStrategy fromRequest = SplitStrategy.from(r.splitStrategy());
        if (fromRequest != null) {
            return fromRequest;
        }
        return SplitStrategy.defaultFor(TaxType.from(r.taxType()));
    }

    private static ShareSplit splitByShares(BigDecimal tax, BigDecimal cgstShare, BigDecimal sgstShare) {
        BigDecimal cgstPct = cgstShare == null ? FIFTY : cgstShare;
        BigDecimal cgst = tax.multiply(cgstPct).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
        BigDecimal sgst = tax.subtract(cgst);
        // If caller sent both and they don't sum to 100, still prefer remainder on SGST for stability.
        if (sgstShare != null && cgstShare != null) {
            BigDecimal expectedSgst = tax.multiply(sgstShare).divide(HUNDRED, SCALE, RoundingMode.HALF_UP);
            // Prefer exact remainder so cgst+sgst == tax; only use expected when close
            if (expectedSgst.subtract(sgst).abs().compareTo(new BigDecimal("0.02")) <= 0) {
                sgst = tax.subtract(cgst);
            }
        }
        return new ShareSplit(cgst, sgst);
    }

    private record ShareSplit(BigDecimal cgst, BigDecimal sgst) {}
}
