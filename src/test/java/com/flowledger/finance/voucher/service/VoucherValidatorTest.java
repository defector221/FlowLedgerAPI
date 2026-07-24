package com.flowledger.finance.voucher.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.flowledger.common.exception.BusinessException;
import com.flowledger.finance.voucher.dto.VoucherDtos.VoucherLineRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VoucherValidatorTest {
    private final VoucherValidator validator = new VoucherValidator();
    private final UUID a1 = UUID.randomUUID();
    private final UUID a2 = UUID.randomUUID();

    @Test
    void acceptsBalancedLines() {
        var totals = validator.validateLines(List.of(
                new VoucherLineRequest(
                        a1,
                        new BigDecimal("250.00"),
                        BigDecimal.ZERO,
                        "Dr",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        1),
                new VoucherLineRequest(
                        a2,
                        BigDecimal.ZERO,
                        new BigDecimal("250.00"),
                        "Cr",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        2)));
        assertEquals(0, totals.totalDebit().compareTo(totals.totalCredit()));
    }

    @Test
    void rejectsUnbalancedLines() {
        assertThrows(
                BusinessException.class,
                () -> validator.validateLines(List.of(
                        new VoucherLineRequest(
                                a1,
                                new BigDecimal("100.00"),
                                BigDecimal.ZERO,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                1),
                        new VoucherLineRequest(
                                a2,
                                BigDecimal.ZERO,
                                new BigDecimal("90.00"),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                2))));
    }

    @Test
    void rejectsSingleLine() {
        assertThrows(
                BusinessException.class,
                () -> validator.validateLines(List.of(new VoucherLineRequest(
                        a1,
                        new BigDecimal("100.00"),
                        BigDecimal.ZERO,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        1))));
    }

    @Test
    void rejectsBothDebitAndCreditOnSameLine() {
        assertThrows(
                BusinessException.class,
                () -> validator.validateLines(List.of(
                        new VoucherLineRequest(
                                a1,
                                new BigDecimal("50.00"),
                                new BigDecimal("50.00"),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                1),
                        new VoucherLineRequest(
                                a2,
                                BigDecimal.ZERO,
                                new BigDecimal("100.00"),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                2))));
    }
}
