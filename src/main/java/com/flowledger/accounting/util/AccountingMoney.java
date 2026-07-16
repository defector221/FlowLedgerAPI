package com.flowledger.accounting.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class AccountingMoney {
    public static final int SCALE = 4;
    public static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    private AccountingMoney() {}

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE, ROUNDING);
    }

    public static BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            return zero();
        }
        return value.setScale(SCALE, ROUNDING);
    }

    public static boolean isZero(BigDecimal value) {
        return normalize(value).compareTo(BigDecimal.ZERO) == 0;
    }

    public static boolean isPositive(BigDecimal value) {
        return normalize(value).compareTo(BigDecimal.ZERO) > 0;
    }
}
