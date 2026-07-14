package com.flowledger.accounting.service;

import com.flowledger.accounting.domain.JournalSource;
import com.flowledger.accounting.domain.PeriodStatus;
import com.flowledger.accounting.entity.Account;
import com.flowledger.accounting.entity.AccountingPeriod;
import com.flowledger.accounting.repository.AccountRepository;
import com.flowledger.accounting.repository.AccountingPeriodRepository;
import com.flowledger.accounting.util.AccountingMoney;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.exception.ConflictException;
import com.flowledger.common.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * Validates journal lines before they are saved or posted: minimum line count, one-sided debit/credit
 * per line, account ownership/activity, manual posting permission, period status and balance.
 */
@Service
public class JournalValidationService {
    private final AccountRepository accounts;
    private final AccountingPeriodRepository periods;

    public JournalValidationService(AccountRepository accounts, AccountingPeriodRepository periods) {
        this.accounts = accounts;
        this.periods = periods;
    }

    public record LineInput(UUID accountId, BigDecimal debitAmount, BigDecimal creditAmount) {}

    public record ValidationResult(AccountingPeriod period, BigDecimal totalDebit, BigDecimal totalCredit) {}

    /**
     * Validates the given lines for the organization/date/source, returning the covering accounting
     * period and normalized totals. Throws BusinessException/ConflictException/ResourceNotFoundException
     * on any violation.
     */
    public ValidationResult validate(UUID organizationId, LocalDate entryDate, JournalSource source, List<LineInput> lines) {
        if (entryDate == null) {
            throw new BusinessException("Entry date is required");
        }
        if (lines == null || lines.size() < 2) {
            throw new BusinessException("A journal entry requires at least two lines");
        }
        BigDecimal totalDebit = AccountingMoney.zero();
        BigDecimal totalCredit = AccountingMoney.zero();
        for (LineInput line : lines) {
            if (line.accountId() == null) {
                throw new BusinessException("Each journal line requires an account");
            }
            BigDecimal debit = AccountingMoney.normalize(line.debitAmount());
            BigDecimal credit = AccountingMoney.normalize(line.creditAmount());
            boolean hasDebit = debit.signum() > 0;
            boolean hasCredit = credit.signum() > 0;
            if (hasDebit == hasCredit) {
                throw new BusinessException("Each journal line must have either a debit or a credit amount, not both or neither");
            }
            if (debit.signum() < 0 || credit.signum() < 0) {
                throw new BusinessException("Journal line amounts cannot be negative");
            }
            Account account = accounts
                    .findByIdAndOrganizationId(line.accountId(), organizationId)
                    .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + line.accountId()));
            if (!account.isActive()) {
                throw new BusinessException("Account is inactive: " + account.getAccountName());
            }
            if (source == JournalSource.MANUAL && !account.isAllowManualPosting()) {
                throw new BusinessException("Manual posting is not allowed for account: " + account.getAccountName());
            }
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
        }
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new BusinessException(
                    "Journal entry is not balanced: total debit " + totalDebit + " does not equal total credit " + totalCredit);
        }
        AccountingPeriod period = periods
                .findCovering(organizationId, entryDate)
                .orElseThrow(() -> new BusinessException("No accounting period covers date " + entryDate));
        if (period.getStatus() != PeriodStatus.OPEN) {
            throw new ConflictException("Accounting period \"" + period.getName() + "\" is not open");
        }
        return new ValidationResult(period, totalDebit, totalCredit);
    }
}
