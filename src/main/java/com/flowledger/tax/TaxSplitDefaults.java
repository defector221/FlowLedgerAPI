package com.flowledger.tax;

import com.flowledger.product.entity.SplitStrategy;
import com.flowledger.product.entity.TaxType;
import java.math.BigDecimal;

/** Defaults and normalize split strategy snapshots on document lines. */
public final class TaxSplitDefaults {
    private static final BigDecimal FIFTY = new BigDecimal("50");

    private TaxSplitDefaults() {}

    public static String normalizeTaxType(String taxType) {
        return TaxType.from(taxType).name();
    }

    public static String normalizeStrategy(String splitStrategy, String taxType) {
        SplitStrategy from = SplitStrategy.from(splitStrategy);
        if (from != null) {
            return from.name();
        }
        return SplitStrategy.defaultFor(TaxType.from(taxType)).name();
    }

    public static BigDecimal cgstShare(String splitStrategy, String taxType, BigDecimal cgstSharePercent) {
        SplitStrategy strategy = SplitStrategy.from(normalizeStrategy(splitStrategy, taxType));
        if (strategy == SplitStrategy.NO_SPLIT_IGST || strategy == SplitStrategy.NO_SPLIT_OTHER) {
            return BigDecimal.ZERO;
        }
        return cgstSharePercent == null ? FIFTY : cgstSharePercent;
    }

    public static BigDecimal sgstShare(String splitStrategy, String taxType, BigDecimal sgstSharePercent) {
        SplitStrategy strategy = SplitStrategy.from(normalizeStrategy(splitStrategy, taxType));
        if (strategy == SplitStrategy.NO_SPLIT_IGST || strategy == SplitStrategy.NO_SPLIT_OTHER) {
            return BigDecimal.ZERO;
        }
        if (sgstSharePercent != null) {
            return sgstSharePercent;
        }
        BigDecimal cgst = cgstShare(splitStrategy, taxType, null);
        return BigDecimal.valueOf(100).subtract(cgst);
    }
}
