package com.flowledger.accounting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.flowledger.accounting.util.AccountingMoney;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TrialBalanceBalanceAssertionTest {
    @Test
    void closingDebitsEqualCreditsWhenBalanced() {
        BigDecimal totalDebit = AccountingMoney.normalize(new BigDecimal("1250.5000"));
        BigDecimal totalCredit = AccountingMoney.normalize(new BigDecimal("1250.5"));
        assertEquals(0, totalDebit.compareTo(totalCredit));
        assertTrue(totalDebit.compareTo(totalCredit) == 0);
    }
}
