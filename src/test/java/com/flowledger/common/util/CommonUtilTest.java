package com.flowledger.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class CommonUtilTest {

    @Test
    void financialYearUsesAprilStart() {
        assertEquals("2026-27", FinancialYearUtil.financialYear(LocalDate.of(2026, 7, 13), "04-01"));
        assertEquals("2025-26", FinancialYearUtil.financialYear(LocalDate.of(2026, 3, 31), "04-01"));
    }

    @Test
    void amountInWordsContainsRupees() {
        String words = AmountInWords.inr(new BigDecimal("1234.50"));
        assertTrue(
                words.toLowerCase().contains("thousand") || words.toLowerCase().contains("rupee"));
    }
}
