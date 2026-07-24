package com.flowledger.accounting.service.reporting;

import com.flowledger.accounting.domain.AccountSubType;
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
import com.flowledger.common.util.FinancialYearUtil;
import com.flowledger.finance.voucher.domain.VoucherStatus;
import com.flowledger.finance.voucher.entity.Voucher;
import com.flowledger.finance.voucher.repository.VoucherRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountingReportService {
    private final AccountRepository accounts;
    private final JournalEntryRepository journals;
    private final JournalEntryLineRepository lines;
    private final VoucherRepository vouchers;

    public AccountingReportService(
            AccountRepository accounts,
            JournalEntryRepository journals,
            JournalEntryLineRepository lines,
            VoucherRepository vouchers) {
        this.accounts = accounts;
        this.journals = journals;
        this.lines = lines;
        this.vouchers = vouchers;
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
                org,
                JournalStatus.POSTED,
                from != null ? from : LocalDate.of(1970, 1, 1),
                to != null ? to : LocalDate.of(2999, 12, 31));
        Map<UUID, Account> accountMap = new HashMap<>();
        for (Account accountEntity : accounts.findByOrganizationIdOrderByAccountCodeAsc(org)) {
            accountMap.put(accountEntity.getId(), accountEntity);
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
                        "UNBALANCED", "Posted journal " + entry.getEntryNumber() + " is unbalanced", entry.getId()));
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
        LocalDate fyStart = FinancialYearUtil.financialYearStart(today, "04-01");
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
        ProfitAndLossResponse pl = profitAndLoss(fyStart, today);
        long journalCount = journals.findByOrganizationIdAndStatusAndEntryDateBetween(
                        org, JournalStatus.POSTED, fyStart, today)
                .size();
        return new DashboardSummaryResponse(
                receivables, payables, cashBank, pl.netProfit(), journalCount, journals.countUnbalancedPosted(org));
    }

    @Transactional(readOnly = true)
    public DayBookResponse dayBook(LocalDate from, LocalDate to) {
        UUID org = TenantContext.getOrganizationId();
        LocalDate fromDate = from != null ? from : LocalDate.of(1970, 1, 1);
        LocalDate toDate = to != null ? to : LocalDate.of(2999, 12, 31);

        List<Voucher> postedVouchers =
                vouchers.findPostedByOrganizationAndDateBetween(org, VoucherStatus.POSTED, fromDate, toDate);
        if (!postedVouchers.isEmpty()) {
            List<DayBookEntry> entries = new ArrayList<>();
            BigDecimal totalDebit = AccountingMoney.zero();
            BigDecimal totalCredit = AccountingMoney.zero();
            for (Voucher voucher : postedVouchers) {
                BigDecimal debit = AccountingMoney.normalize(voucher.getTotalDebit());
                BigDecimal credit = AccountingMoney.normalize(voucher.getTotalCredit());
                entries.add(new DayBookEntry(
                        voucher.getId(),
                        voucher.getVoucherNumber(),
                        voucher.getVoucherDate(),
                        voucher.getVoucherType() != null
                                ? voucher.getVoucherType().name()
                                : "VOUCHER",
                        voucher.getNarration(),
                        "VOUCHER",
                        debit,
                        credit));
                totalDebit = totalDebit.add(debit);
                totalCredit = totalCredit.add(credit);
            }
            return new DayBookResponse(from, to, "VOUCHER", entries, totalDebit, totalCredit, entries.size());
        }

        List<JournalEntry> entries =
                journals.findByOrganizationIdAndStatusAndEntryDateBetween(org, JournalStatus.POSTED, fromDate, toDate);
        entries.sort(Comparator.comparing(JournalEntry::getEntryDate)
                .thenComparing(JournalEntry::getEntryNumber, Comparator.nullsLast(String::compareTo)));
        List<DayBookEntry> rows = new ArrayList<>();
        BigDecimal totalDebit = AccountingMoney.zero();
        BigDecimal totalCredit = AccountingMoney.zero();
        for (JournalEntry entry : entries) {
            BigDecimal debit = AccountingMoney.normalize(entry.getTotalDebit());
            BigDecimal credit = AccountingMoney.normalize(entry.getTotalCredit());
            rows.add(new DayBookEntry(
                    entry.getId(),
                    entry.getEntryNumber(),
                    entry.getEntryDate(),
                    entry.getVoucherType() != null ? entry.getVoucherType().name() : "JOURNAL",
                    entry.getDescription(),
                    "JOURNAL",
                    debit,
                    credit));
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
        }
        return new DayBookResponse(from, to, "JOURNAL", rows, totalDebit, totalCredit, rows.size());
    }

    @Transactional(readOnly = true)
    public CashBookResponse cashBook(LocalDate from, LocalDate to) {
        return moneyBook(from, to, "CASH", SystemAccountKey.CASH, AccountSubType.CASH, "CASH");
    }

    @Transactional(readOnly = true)
    public CashBookResponse bankBook(LocalDate from, LocalDate to) {
        return moneyBook(from, to, "BANK", SystemAccountKey.BANK, AccountSubType.BANK, "BANK");
    }

    @Transactional(readOnly = true)
    public CashFlowResponse cashFlow(LocalDate from, LocalDate to) {
        UUID org = TenantContext.getOrganizationId();
        LocalDate fromDate = from != null ? from : LocalDate.of(1970, 1, 1);
        LocalDate toDate = to != null ? to : LocalDate.of(2999, 12, 31);

        ProfitAndLossResponse pl = profitAndLoss(fromDate, toDate);
        BigDecimal netProfit = AccountingMoney.normalize(pl.netProfit());

        Map<UUID, MutableTotals> periodTotals = accumulate(org, fromDate, toDate);
        BigDecimal arDelta = AccountingMoney.zero();
        BigDecimal apDelta = AccountingMoney.zero();
        BigDecimal inventoryDelta = AccountingMoney.zero();
        for (Account account : accounts.findByOrganizationIdOrderByAccountCodeAsc(org)) {
            MutableTotals t = periodTotals.getOrDefault(account.getId(), new MutableTotals());
            BigDecimal movement = t.debit.subtract(t.credit);
            if (matchesSystemOrSubtype(
                            account, SystemAccountKey.ACCOUNTS_RECEIVABLE, AccountSubType.ACCOUNTS_RECEIVABLE)
                    || containsIgnoreCase(account, "RECEIVABLE")
                    || containsIgnoreCase(account, "DEBTOR")) {
                arDelta = arDelta.add(movement);
            } else if (matchesSystemOrSubtype(
                            account, SystemAccountKey.ACCOUNTS_PAYABLE, AccountSubType.ACCOUNTS_PAYABLE)
                    || containsIgnoreCase(account, "PAYABLE")
                    || containsIgnoreCase(account, "CREDITOR")) {
                // liability: credit increase is positive cash (AP increase)
                apDelta = apDelta.add(t.credit.subtract(t.debit));
            } else if (matchesSystemOrSubtype(account, SystemAccountKey.INVENTORY, AccountSubType.INVENTORY)
                    || containsIgnoreCase(account, "INVENTORY")
                    || containsIgnoreCase(account, "STOCK")) {
                inventoryDelta = inventoryDelta.add(movement);
            }
        }

        // Indirect: decrease in AR/Inventory = cash in; increase in AP = cash in
        BigDecimal arAdjustment = arDelta.negate();
        BigDecimal inventoryAdjustment = inventoryDelta.negate();
        BigDecimal apAdjustment = apDelta;

        List<NamedAmount> operatingItems = List.of(new NamedAmount("Net profit", netProfit));
        CashFlowSection operating = new CashFlowSection("Operating", operatingItems, netProfit);

        List<NamedAmount> wcItems = new ArrayList<>();
        wcItems.add(new NamedAmount("Change in accounts receivable", arAdjustment));
        wcItems.add(new NamedAmount("Change in inventory", inventoryAdjustment));
        wcItems.add(new NamedAmount("Change in accounts payable", apAdjustment));
        BigDecimal wcTotal = arAdjustment.add(inventoryAdjustment).add(apAdjustment);
        CashFlowSection workingCapital = new CashFlowSection("Working capital", wcItems, wcTotal);

        BigDecimal netOps = netProfit.add(wcTotal);

        Set<UUID> cashBankIds = resolveMoneyAccountIds(
                org,
                Set.of(SystemAccountKey.CASH, SystemAccountKey.BANK),
                Set.of(AccountSubType.CASH, AccountSubType.BANK),
                Set.of("CASH", "BANK"));
        BigDecimal openingCash = balanceOfAccounts(org, cashBankIds, null, fromDate.minusDays(1));
        BigDecimal closingCash = balanceOfAccounts(org, cashBankIds, null, toDate);
        BigDecimal netChange = closingCash.subtract(openingCash);

        return new CashFlowResponse(
                from, to, netProfit, operating, workingCapital, netOps, openingCash, closingCash, netChange);
    }

    private CashBookResponse moneyBook(
            LocalDate from,
            LocalDate to,
            String bookType,
            SystemAccountKey systemKey,
            AccountSubType subType,
            String nameToken) {
        UUID org = TenantContext.getOrganizationId();
        LocalDate fromDate = from != null ? from : LocalDate.of(1970, 1, 1);
        LocalDate toDate = to != null ? to : LocalDate.of(2999, 12, 31);

        Set<UUID> accountIds = resolveMoneyAccountIds(org, Set.of(systemKey), Set.of(subType), Set.of(nameToken));
        Map<UUID, Account> accountMap = new HashMap<>();
        for (Account account : accounts.findByOrganizationIdOrderByAccountCodeAsc(org)) {
            if (accountIds.contains(account.getId())) {
                accountMap.put(account.getId(), account);
            }
        }

        BigDecimal opening = balanceOfAccounts(org, accountIds, null, fromDate.minusDays(1));

        List<JournalEntry> entries =
                journals.findByOrganizationIdAndStatusAndEntryDateBetween(org, JournalStatus.POSTED, fromDate, toDate);
        entries.sort(Comparator.comparing(JournalEntry::getEntryDate)
                .thenComparing(JournalEntry::getEntryNumber, Comparator.nullsLast(String::compareTo)));

        List<CashBookLine> bookLines = new ArrayList<>();
        BigDecimal running = opening;
        BigDecimal totalDebit = AccountingMoney.zero();
        BigDecimal totalCredit = AccountingMoney.zero();
        for (JournalEntry entry : entries) {
            for (JournalEntryLine line : lines.findByJournalEntryIdOrderByLineNumberAsc(entry.getId())) {
                if (!accountIds.contains(line.getAccountId())) {
                    continue;
                }
                Account account = accountMap.get(line.getAccountId());
                BigDecimal debit = AccountingMoney.normalize(line.getDebitAmount());
                BigDecimal credit = AccountingMoney.normalize(line.getCreditAmount());
                running = running.add(debit).subtract(credit);
                totalDebit = totalDebit.add(debit);
                totalCredit = totalCredit.add(credit);
                bookLines.add(new CashBookLine(
                        entry.getId(),
                        entry.getEntryNumber(),
                        entry.getEntryDate(),
                        line.getAccountId(),
                        account != null ? account.getAccountCode() : null,
                        account != null ? account.getAccountName() : null,
                        line.getDescription() != null ? line.getDescription() : entry.getDescription(),
                        debit,
                        credit,
                        running));
            }
        }
        return new CashBookResponse(from, to, bookType, opening, bookLines, totalDebit, totalCredit, running);
    }

    private Set<UUID> resolveMoneyAccountIds(
            UUID org, Set<SystemAccountKey> keys, Set<AccountSubType> subTypes, Set<String> tokens) {
        Set<UUID> ids = new HashSet<>();
        for (Account account : accounts.findByOrganizationIdOrderByAccountCodeAsc(org)) {
            if (account.getSystemAccountKey() != null && keys.contains(account.getSystemAccountKey())) {
                ids.add(account.getId());
                continue;
            }
            if (account.getAccountSubType() != null && subTypes.contains(account.getAccountSubType())) {
                ids.add(account.getId());
                continue;
            }
            for (String token : tokens) {
                if (containsIgnoreCase(account, token)) {
                    ids.add(account.getId());
                    break;
                }
            }
        }
        return ids;
    }

    private BigDecimal balanceOfAccounts(UUID org, Set<UUID> accountIds, LocalDate from, LocalDate to) {
        if (accountIds.isEmpty()) {
            return AccountingMoney.zero();
        }
        BigDecimal balance = AccountingMoney.zero();
        Map<UUID, MutableTotals> totals = accumulate(org, from, to);
        for (Account account : accounts.findByOrganizationIdOrderByAccountCodeAsc(org)) {
            if (!accountIds.contains(account.getId())) {
                continue;
            }
            MutableTotals t = totals.getOrDefault(account.getId(), new MutableTotals());
            balance = balance.add(AccountingMoney.normalize(account.getOpeningDebit()))
                    .subtract(AccountingMoney.normalize(account.getOpeningCredit()))
                    .add(t.debit)
                    .subtract(t.credit);
        }
        return balance;
    }

    private static boolean matchesSystemOrSubtype(Account account, SystemAccountKey key, AccountSubType subType) {
        return account.getSystemAccountKey() == key || account.getAccountSubType() == subType;
    }

    private static boolean containsIgnoreCase(Account account, String token) {
        String upper = token.toUpperCase();
        return (account.getAccountCode() != null
                        && account.getAccountCode().toUpperCase().contains(upper))
                || (account.getAccountName() != null
                        && account.getAccountName().toUpperCase().contains(upper));
    }

    private Map<UUID, MutableTotals> accumulate(UUID org, LocalDate from, LocalDate to) {
        LocalDate fromDate = from != null ? from : LocalDate.of(1970, 1, 1);
        LocalDate toDate = to != null ? to : LocalDate.of(2999, 12, 31);
        Map<UUID, MutableTotals> totals = new HashMap<>();
        for (JournalEntry entry : journals.findByOrganizationIdAndStatusAndEntryDateBetween(
                org, JournalStatus.POSTED, fromDate, toDate)) {
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
