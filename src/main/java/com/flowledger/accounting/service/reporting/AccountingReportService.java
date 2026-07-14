package com.flowledger.accounting.service.reporting;

import com.flowledger.accounting.domain.AccountType;
import com.flowledger.accounting.domain.JournalStatus;
import com.flowledger.accounting.domain.SystemAccountKey;
import com.flowledger.accounting.dto.AccountingDtos.*;
import com.flowledger.accounting.entity.Account;
import com.flowledger.accounting.entity.JournalEntry;
import com.flowledger.accounting.entity.JournalEntryLine;
import com.flowledger.accounting.repository.AccountRepository;
import com.flowledger.accounting.repository.JournalEntryLineRepository;
import com.flowledger.accounting.repository.JournalEntryRepository;
import com.flowledger.accounting.util.AccountingMoney;
import com.flowledger.common.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountingReportService {
    private final AccountRepository accounts;
    private final JournalEntryRepository journals;
    private final JournalEntryLineRepository lines;

    public AccountingReportService(
            AccountRepository accounts, JournalEntryRepository journals, JournalEntryLineRepository lines) {
        this.accounts = accounts;
        this.journals = journals;
        this.lines = lines;
    }

    @Transactional(readOnly = true)
    public TrialBalanceResponse trialBalance(LocalDate from, LocalDate to) {
        UUID org = TenantContext.getOrganizationId();
        Map<UUID, MutableTotals> totals = accumulate(org, from, to);
        List<TrialBalanceRow> rows = new ArrayList<>();
        BigDecimal totalDebit = AccountingMoney.zero();
        BigDecimal totalCredit = AccountingMoney.zero();
        for (Account account : accounts.findByOrganizationIdOrderByAccountCodeAsc(org)) {
            MutableTotals t = totals.getOrDefault(account.getId(), new MutableTotals());
            BigDecimal openingDebit = AccountingMoney.normalize(account.getOpeningDebit());
            BigDecimal openingCredit = AccountingMoney.normalize(account.getOpeningCredit());
            BigDecimal closingDebit = openingDebit.add(t.debit);
            BigDecimal closingCredit = openingCredit.add(t.credit);
            BigDecimal net = closingDebit.subtract(closingCredit);
            BigDecimal outDebit = net.signum() >= 0 ? net : AccountingMoney.zero();
            BigDecimal outCredit = net.signum() < 0 ? net.abs() : AccountingMoney.zero();
            if (openingDebit.signum() == 0
                    && openingCredit.signum() == 0
                    && t.debit.signum() == 0
                    && t.credit.signum() == 0) {
                continue;
            }
            rows.add(new TrialBalanceRow(
                    account.getId(),
                    account.getAccountCode(),
                    account.getAccountName(),
                    account.getAccountType(),
                    openingDebit,
                    openingCredit,
                    t.debit,
                    t.credit,
                    outDebit,
                    outCredit));
            totalDebit = totalDebit.add(outDebit);
            totalCredit = totalCredit.add(outCredit);
        }
        return new TrialBalanceResponse(
                from, to, rows, totalDebit, totalCredit, totalDebit.compareTo(totalCredit) == 0);
    }

    @Transactional(readOnly = true)
    public ProfitAndLossResponse profitAndLoss(LocalDate from, LocalDate to) {
        UUID org = TenantContext.getOrganizationId();
        Map<UUID, MutableTotals> totals = accumulate(org, from, to);
        List<NamedAmount> income = new ArrayList<>();
        List<NamedAmount> expenses = new ArrayList<>();
        BigDecimal totalIncome = AccountingMoney.zero();
        BigDecimal totalExpenses = AccountingMoney.zero();
        for (Account account : accounts.findByOrganizationIdOrderByAccountCodeAsc(org)) {
            MutableTotals t = totals.getOrDefault(account.getId(), new MutableTotals());
            if (account.getAccountType() == AccountType.REVENUE) {
                BigDecimal amount = t.credit.subtract(t.debit);
                if (amount.signum() != 0) {
                    income.add(new NamedAmount(account.getAccountName(), amount));
                    totalIncome = totalIncome.add(amount);
                }
            } else if (account.getAccountType() == AccountType.EXPENSE) {
                BigDecimal amount = t.debit.subtract(t.credit);
                if (amount.signum() != 0) {
                    expenses.add(new NamedAmount(account.getAccountName(), amount));
                    totalExpenses = totalExpenses.add(amount);
                }
            }
        }
        return new ProfitAndLossResponse(
                from, to, income, expenses, totalIncome, totalExpenses, totalIncome.subtract(totalExpenses));
    }

    @Transactional(readOnly = true)
    public BalanceSheetResponse balanceSheet(LocalDate asOf) {
        UUID org = TenantContext.getOrganizationId();
        Map<UUID, MutableTotals> totals = accumulate(org, null, asOf);
        List<NamedAmount> assets = new ArrayList<>();
        List<NamedAmount> liabilities = new ArrayList<>();
        List<NamedAmount> equity = new ArrayList<>();
        BigDecimal totalAssets = AccountingMoney.zero();
        BigDecimal totalLiabilities = AccountingMoney.zero();
        BigDecimal totalEquity = AccountingMoney.zero();
        for (Account account : accounts.findByOrganizationIdOrderByAccountCodeAsc(org)) {
            MutableTotals t = totals.getOrDefault(account.getId(), new MutableTotals());
            BigDecimal balance = AccountingMoney.normalize(account.getOpeningDebit())
                    .subtract(AccountingMoney.normalize(account.getOpeningCredit()))
                    .add(t.debit)
                    .subtract(t.credit);
            if (balance.signum() == 0) {
                continue;
            }
            switch (account.getAccountType()) {
                case ASSET -> {
                    assets.add(new NamedAmount(account.getAccountName(), balance));
                    totalAssets = totalAssets.add(balance);
                }
                case LIABILITY -> {
                    BigDecimal creditBal = balance.negate();
                    liabilities.add(new NamedAmount(account.getAccountName(), creditBal));
                    totalLiabilities = totalLiabilities.add(creditBal);
                }
                case EQUITY -> {
                    BigDecimal creditBal = balance.negate();
                    equity.add(new NamedAmount(account.getAccountName(), creditBal));
                    totalEquity = totalEquity.add(creditBal);
                }
                case REVENUE, EXPENSE -> {
                    // rolled into retained-style net for statement date via P&L
                }
            }
        }
        ProfitAndLossResponse pl = profitAndLoss(LocalDate.of(asOf.getYear(), 1, 1), asOf);
        if (pl.netProfit().signum() != 0) {
            equity.add(new NamedAmount("Current Year P&L", pl.netProfit()));
            totalEquity = totalEquity.add(pl.netProfit());
        }
        boolean balanced = totalAssets.compareTo(totalLiabilities.add(totalEquity)) == 0;
        return new BalanceSheetResponse(
                asOf, assets, liabilities, equity, totalAssets, totalLiabilities, totalEquity, balanced);
    }

    @Transactional(readOnly = true)
    public GstSummaryResponse gstSummary(LocalDate from, LocalDate to) {
        UUID org = TenantContext.getOrganizationId();
        Map<SystemAccountKey, BigDecimal> nets = new EnumMap<>(SystemAccountKey.class);
        for (SystemAccountKey key : List.of(
                SystemAccountKey.OUTPUT_CGST,
                SystemAccountKey.OUTPUT_SGST,
                SystemAccountKey.OUTPUT_IGST,
                SystemAccountKey.INPUT_CGST,
                SystemAccountKey.INPUT_SGST,
                SystemAccountKey.INPUT_IGST)) {
            nets.put(key, AccountingMoney.zero());
        }
        Map<UUID, MutableTotals> totals = accumulate(org, from, to);
        for (Account account : accounts.findByOrganizationIdOrderByAccountCodeAsc(org)) {
            if (account.getSystemAccountKey() == null || !nets.containsKey(account.getSystemAccountKey())) {
                continue;
            }
            MutableTotals t = totals.getOrDefault(account.getId(), new MutableTotals());
            BigDecimal net = t.credit.subtract(t.debit);
            if (account.getSystemAccountKey().name().startsWith("INPUT")) {
                net = t.debit.subtract(t.credit);
            }
            nets.put(account.getSystemAccountKey(), net);
        }
        BigDecimal output = nets.get(SystemAccountKey.OUTPUT_CGST)
                .add(nets.get(SystemAccountKey.OUTPUT_SGST))
                .add(nets.get(SystemAccountKey.OUTPUT_IGST));
        BigDecimal input = nets.get(SystemAccountKey.INPUT_CGST)
                .add(nets.get(SystemAccountKey.INPUT_SGST))
                .add(nets.get(SystemAccountKey.INPUT_IGST));
        return new GstSummaryResponse(
                from,
                to,
                nets.get(SystemAccountKey.OUTPUT_CGST),
                nets.get(SystemAccountKey.OUTPUT_SGST),
                nets.get(SystemAccountKey.OUTPUT_IGST),
                nets.get(SystemAccountKey.INPUT_CGST),
                nets.get(SystemAccountKey.INPUT_SGST),
                nets.get(SystemAccountKey.INPUT_IGST),
                output.subtract(input));
    }

    @Transactional(readOnly = true)
    public List<GlLineResponse> generalLedger(LocalDate from, LocalDate to) {
        UUID org = TenantContext.getOrganizationId();
        List<GlLineResponse> result = new ArrayList<>();
        List<JournalEntry> entries = journals.findByOrganizationIdAndStatusAndEntryDateBetween(
                org, JournalStatus.POSTED, from != null ? from : LocalDate.of(1970, 1, 1), to != null ? to : LocalDate.of(2999, 12, 31));
        Map<UUID, Account> accountMap = new HashMap<>();
        for (Account a : accounts.findByOrganizationIdOrderByAccountCodeAsc(org)) {
            accountMap.put(a.getId(), a);
        }
        for (JournalEntry entry : entries) {
            for (JournalEntryLine line : lines.findByJournalEntryIdOrderByLineNumberAsc(entry.getId())) {
                Account account = accountMap.get(line.getAccountId());
                result.add(new GlLineResponse(
                        entry.getId(),
                        entry.getEntryNumber(),
                        entry.getEntryDate(),
                        line.getAccountId(),
                        account != null ? account.getAccountCode() : null,
                        account != null ? account.getAccountName() : null,
                        line.getDescription(),
                        line.getDebitAmount(),
                        line.getCreditAmount()));
            }
        }
        return result;
    }

    @Transactional(readOnly = true)
    public IntegrityCheckResponse integrityCheck() {
        UUID org = TenantContext.getOrganizationId();
        List<IntegrityIssue> issues = new ArrayList<>();
        for (JournalEntry entry : journals.findByOrganizationIdAndStatusAndEntryDateBetween(
                org, JournalStatus.POSTED, LocalDate.of(1970, 1, 1), LocalDate.of(2999, 12, 31))) {
            if (entry.getTotalDebit().compareTo(entry.getTotalCredit()) != 0) {
                issues.add(new IntegrityIssue(
                        "UNBALANCED",
                        "Posted journal " + entry.getEntryNumber() + " is unbalanced",
                        entry.getId()));
            }
            List<JournalEntryLine> journalLines = lines.findByJournalEntryIdOrderByLineNumberAsc(entry.getId());
            BigDecimal lineDebit = AccountingMoney.zero();
            BigDecimal lineCredit = AccountingMoney.zero();
            for (JournalEntryLine line : journalLines) {
                lineDebit = lineDebit.add(AccountingMoney.normalize(line.getDebitAmount()));
                lineCredit = lineCredit.add(AccountingMoney.normalize(line.getCreditAmount()));
            }
            if (lineDebit.compareTo(entry.getTotalDebit()) != 0 || lineCredit.compareTo(entry.getTotalCredit()) != 0) {
                issues.add(new IntegrityIssue(
                        "TOTAL_MISMATCH",
                        "Line totals do not match header for " + entry.getEntryNumber(),
                        entry.getId()));
            }
        }
        long unbalanced = journals.countUnbalancedPosted(org);
        if (unbalanced > 0 && issues.isEmpty()) {
            issues.add(new IntegrityIssue("UNBALANCED_COUNT", unbalanced + " unbalanced posted journals", null));
        }
        return new IntegrityCheckResponse(issues.isEmpty(), issues);
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse dashboard() {
        UUID org = TenantContext.getOrganizationId();
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        TrialBalanceResponse tb = trialBalance(null, today);
        BigDecimal receivables = AccountingMoney.zero();
        BigDecimal payables = AccountingMoney.zero();
        BigDecimal cashBank = AccountingMoney.zero();
        for (TrialBalanceRow row : tb.rows()) {
            Account account = accounts.findById(row.accountId()).orElse(null);
            if (account == null || account.getSystemAccountKey() == null) {
                continue;
            }
            BigDecimal net = row.closingDebit().subtract(row.closingCredit());
            switch (account.getSystemAccountKey()) {
                case ACCOUNTS_RECEIVABLE -> receivables = receivables.add(net);
                case ACCOUNTS_PAYABLE -> payables = payables.add(net.negate());
                case CASH, BANK -> cashBank = cashBank.add(net);
                default -> {}
            }
        }
        ProfitAndLossResponse pl = profitAndLoss(monthStart, today);
        long journalCount = journals
                .findByOrganizationIdAndStatusAndEntryDateBetween(org, JournalStatus.POSTED, monthStart, today)
                .size();
        return new DashboardSummaryResponse(
                receivables,
                payables,
                cashBank,
                pl.netProfit(),
                journalCount,
                journals.countUnbalancedPosted(org));
    }

    private Map<UUID, MutableTotals> accumulate(UUID org, LocalDate from, LocalDate to) {
        LocalDate fromDate = from != null ? from : LocalDate.of(1970, 1, 1);
        LocalDate toDate = to != null ? to : LocalDate.of(2999, 12, 31);
        Map<UUID, MutableTotals> totals = new HashMap<>();
        for (JournalEntry entry :
                journals.findByOrganizationIdAndStatusAndEntryDateBetween(org, JournalStatus.POSTED, fromDate, toDate)) {
            // include REVERSED originals and their compensating SYSTEM posts (both POSTED historically —
            // reversed originals flip to REVERSED status so they drop out; compensating remains POSTED)
            for (JournalEntryLine line : lines.findByJournalEntryIdOrderByLineNumberAsc(entry.getId())) {
                MutableTotals t = totals.computeIfAbsent(line.getAccountId(), id -> new MutableTotals());
                t.debit = t.debit.add(AccountingMoney.normalize(line.getDebitAmount()));
                t.credit = t.credit.add(AccountingMoney.normalize(line.getCreditAmount()));
            }
        }
        // Also include REVERSED originals that were marked REVERSED (they should still appear with compensating)
        // Plan: original stays POSTED then marked REVERSED — both included so net offsets.
        // Re-read REVERSED in range and include them so nets cancel with SYSTEM reversal.
        for (JournalEntry entry : journals.findByOrganizationIdAndStatusAndEntryDateBetween(
                org, JournalStatus.REVERSED, fromDate, toDate)) {
            for (JournalEntryLine line : lines.findByJournalEntryIdOrderByLineNumberAsc(entry.getId())) {
                MutableTotals t = totals.computeIfAbsent(line.getAccountId(), id -> new MutableTotals());
                t.debit = t.debit.add(AccountingMoney.normalize(line.getDebitAmount()));
                t.credit = t.credit.add(AccountingMoney.normalize(line.getCreditAmount()));
            }
        }
        return totals;
    }

    private static final class MutableTotals {
        private BigDecimal debit = AccountingMoney.zero();
        private BigDecimal credit = AccountingMoney.zero();
    }
}
