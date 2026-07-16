package com.flowledger.common.util;

import java.time.LocalDate;
import java.time.MonthDay;

public final class FinancialYearUtil {
    private FinancialYearUtil() {}

    public static String financialYear(LocalDate date, String startMonthDay) {
        MonthDay start = MonthDay.parse("--" + startMonthDay);
        int startYear = date.isBefore(start.atYear(date.getYear())) ? date.getYear() - 1 : date.getYear();
        return startYear + "-" + String.format("%02d", (startYear + 1) % 100);
    }

    /** First day of the financial year that contains {@code date} (e.g. 04-01 → 1 Apr). */
    public static LocalDate financialYearStart(LocalDate date, String startMonthDay) {
        MonthDay start = MonthDay.parse("--" + startMonthDay);
        int startYear = date.isBefore(start.atYear(date.getYear())) ? date.getYear() - 1 : date.getYear();
        return start.atYear(startYear);
    }
}
