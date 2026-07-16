package com.flowledger.accounting.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.flowledger.accounting.domain.JournalSource;
import com.flowledger.accounting.domain.PeriodStatus;
import com.flowledger.accounting.entity.Account;
import com.flowledger.accounting.entity.AccountingPeriod;
import com.flowledger.accounting.repository.AccountRepository;
import com.flowledger.accounting.repository.AccountingPeriodRepository;
import com.flowledger.common.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JournalValidationServiceTest {
    @Mock
    AccountRepository accounts;

    @Mock
    AccountingPeriodRepository periods;

    JournalValidationService service;
    UUID org = UUID.randomUUID();
    UUID a1 = UUID.randomUUID();
    UUID a2 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new JournalValidationService(accounts, periods);
        Account cash = account(a1, true);
        Account sales = account(a2, true);
        when(accounts.findByIdAndOrganizationId(eq(a1), eq(org))).thenReturn(Optional.of(cash));
        when(accounts.findByIdAndOrganizationId(eq(a2), eq(org))).thenReturn(Optional.of(sales));
        AccountingPeriod period = new AccountingPeriod();
        period.setId(UUID.randomUUID());
        period.setFiscalYearId(UUID.randomUUID());
        period.setName("July 2026");
        period.setStatus(PeriodStatus.OPEN);
        when(periods.findCovering(eq(org), any(LocalDate.class))).thenReturn(Optional.of(period));
    }

    @Test
    void acceptsBalancedLines() {
        var result = service.validate(
                org,
                LocalDate.of(2026, 7, 1),
                JournalSource.MANUAL,
                List.of(
                        new JournalValidationService.LineInput(a1, new BigDecimal("100.00"), BigDecimal.ZERO),
                        new JournalValidationService.LineInput(a2, BigDecimal.ZERO, new BigDecimal("100.00"))));
        assertEquals(0, result.totalDebit().compareTo(result.totalCredit()));
    }

    @Test
    void rejectsUnbalancedLines() {
        assertThrows(
                BusinessException.class,
                () -> service.validate(
                        org,
                        LocalDate.of(2026, 7, 1),
                        JournalSource.MANUAL,
                        List.of(
                                new JournalValidationService.LineInput(a1, new BigDecimal("100.00"), BigDecimal.ZERO),
                                new JournalValidationService.LineInput(a2, BigDecimal.ZERO, new BigDecimal("90.00")))));
    }

    @Test
    void rejectsTwoSidedLine() {
        assertThrows(
                BusinessException.class,
                () -> service.validate(
                        org,
                        LocalDate.of(2026, 7, 1),
                        JournalSource.MANUAL,
                        List.of(
                                new JournalValidationService.LineInput(
                                        a1, new BigDecimal("50.00"), new BigDecimal("50.00")),
                                new JournalValidationService.LineInput(a2, BigDecimal.ZERO, new BigDecimal("100.00")))));
    }

    private static Account account(UUID id, boolean active) {
        Account a = new Account();
        a.setId(id);
        a.setActive(active);
        a.setAllowManualPosting(true);
        a.setAccountName("Test");
        return a;
    }
}
