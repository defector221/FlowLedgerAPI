package com.flowledger.accounting.service;

import com.flowledger.accounting.domain.*;
import com.flowledger.accounting.entity.Account;
import com.flowledger.accounting.entity.AccountingPeriod;
import com.flowledger.accounting.entity.FiscalYear;
import com.flowledger.accounting.repository.AccountRepository;
import com.flowledger.accounting.repository.AccountingPeriodRepository;
import com.flowledger.accounting.repository.FiscalYearRepository;
import com.flowledger.accounting.util.AccountingMoney;
import com.flowledger.common.util.FinancialYearUtil;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChartOfAccountsBootstrap {
    private final AccountRepository accounts;
    private final FiscalYearRepository fiscalYears;
    private final AccountingPeriodRepository periods;

    public ChartOfAccountsBootstrap(
            AccountRepository accounts, FiscalYearRepository fiscalYears, AccountingPeriodRepository periods) {
        this.accounts = accounts;
        this.fiscalYears = fiscalYears;
        this.periods = periods;
    }

    @Transactional
    public void initializeOrganization(UUID orgId, String financialYearStart) {
        if (!accounts.existsByOrganizationIdAndSystemAccountKeyIsNotNull(orgId)) {
            seedAccounts(orgId);
        }
        if (fiscalYears.findByOrganizationIdAndCurrentTrue(orgId).isEmpty()) {
            ensureCurrentFiscalYear(orgId, financialYearStart == null ? "04-01" : financialYearStart);
        }
    }

    private void seedAccounts(UUID orgId) {
        create(orgId, "1000", "Cash", AccountType.ASSET, AccountSubType.CASH, SystemAccountKey.CASH);
        create(orgId, "1010", "Bank", AccountType.ASSET, AccountSubType.BANK, SystemAccountKey.BANK);
        create(
                orgId,
                "1100",
                "Accounts Receivable",
                AccountType.ASSET,
                AccountSubType.ACCOUNTS_RECEIVABLE,
                SystemAccountKey.ACCOUNTS_RECEIVABLE);
        create(
                orgId,
                "1200",
                "Inventory",
                AccountType.ASSET,
                AccountSubType.INVENTORY,
                SystemAccountKey.INVENTORY);
        create(
                orgId,
                "1300",
                "Input CGST",
                AccountType.ASSET,
                AccountSubType.TAX_RECEIVABLE,
                SystemAccountKey.INPUT_CGST);
        create(
                orgId,
                "1310",
                "Input SGST",
                AccountType.ASSET,
                AccountSubType.TAX_RECEIVABLE,
                SystemAccountKey.INPUT_SGST);
        create(
                orgId,
                "1320",
                "Input IGST",
                AccountType.ASSET,
                AccountSubType.TAX_RECEIVABLE,
                SystemAccountKey.INPUT_IGST);
        create(
                orgId,
                "2000",
                "Accounts Payable",
                AccountType.LIABILITY,
                AccountSubType.ACCOUNTS_PAYABLE,
                SystemAccountKey.ACCOUNTS_PAYABLE);
        create(
                orgId,
                "2100",
                "Output CGST",
                AccountType.LIABILITY,
                AccountSubType.TAX_PAYABLE,
                SystemAccountKey.OUTPUT_CGST);
        create(
                orgId,
                "2110",
                "Output SGST",
                AccountType.LIABILITY,
                AccountSubType.TAX_PAYABLE,
                SystemAccountKey.OUTPUT_SGST);
        create(
                orgId,
                "2120",
                "Output IGST",
                AccountType.LIABILITY,
                AccountSubType.TAX_PAYABLE,
                SystemAccountKey.OUTPUT_IGST);
        create(orgId, "3000", "Capital", AccountType.EQUITY, AccountSubType.CAPITAL, SystemAccountKey.CAPITAL);
        create(
                orgId,
                "3100",
                "Retained Earnings",
                AccountType.EQUITY,
                AccountSubType.RETAINED_EARNINGS,
                SystemAccountKey.RETAINED_EARNINGS);
        create(orgId, "4000", "Sales", AccountType.REVENUE, AccountSubType.SALES, SystemAccountKey.SALES);
        create(
                orgId,
                "4100",
                "Other Income",
                AccountType.REVENUE,
                AccountSubType.INDIRECT_INCOME,
                SystemAccountKey.OTHER_INCOME);
        create(
                orgId,
                "4200",
                "Discount Received",
                AccountType.REVENUE,
                AccountSubType.DISCOUNT_RECEIVED,
                SystemAccountKey.DISCOUNT_RECEIVED);
        create(
                orgId,
                "4300",
                "Round Off Income",
                AccountType.REVENUE,
                AccountSubType.ROUND_OFF,
                SystemAccountKey.ROUND_OFF_INCOME);
        create(orgId, "5000", "Purchases", AccountType.EXPENSE, AccountSubType.PURCHASE, SystemAccountKey.PURCHASE);
        create(
                orgId,
                "5100",
                "Cost of Goods Sold",
                AccountType.EXPENSE,
                AccountSubType.COST_OF_GOODS_SOLD,
                SystemAccountKey.COGS);
        create(
                orgId,
                "5200",
                "Operating Expenses",
                AccountType.EXPENSE,
                AccountSubType.INDIRECT_EXPENSE,
                SystemAccountKey.OPERATING_EXPENSES);
        create(
                orgId,
                "5300",
                "Discount Allowed",
                AccountType.EXPENSE,
                AccountSubType.DISCOUNT_ALLOWED,
                SystemAccountKey.DISCOUNT_ALLOWED);
        create(
                orgId,
                "5400",
                "Round Off Expense",
                AccountType.EXPENSE,
                AccountSubType.ROUND_OFF,
                SystemAccountKey.ROUND_OFF_EXPENSE);
    }

    private void create(
            UUID orgId,
            String code,
            String name,
            AccountType type,
            AccountSubType subType,
            SystemAccountKey key) {
        Account a = new Account();
        a.setOrganizationId(orgId);
        a.setAccountCode(code);
        a.setAccountName(name);
        a.setAccountType(type);
        a.setAccountSubType(subType);
        a.setSystemAccountKey(key);
        a.setSystemAccount(true);
        a.setActive(true);
        a.setAllowManualPosting(true);
        a.setOpeningDebit(AccountingMoney.normalize(BigDecimal.ZERO));
        a.setOpeningCredit(AccountingMoney.normalize(BigDecimal.ZERO));
        accounts.save(a);
    }

    public FiscalYear ensureCurrentFiscalYear(UUID orgId, String financialYearStart) {
        LocalDate today = LocalDate.now();
        MonthDay startMd = MonthDay.parse("--" + financialYearStart);
        int startYear = today.isBefore(startMd.atYear(today.getYear())) ? today.getYear() - 1 : today.getYear();
        LocalDate start = startMd.atYear(startYear);
        LocalDate end = start.plusYears(1).minusDays(1);
        String name = FinancialYearUtil.financialYear(today, financialYearStart);

        FiscalYear fy = new FiscalYear();
        fy.setOrganizationId(orgId);
        fy.setName(name);
        fy.setStartDate(start);
        fy.setEndDate(end);
        fy.setStatus(FiscalYearStatus.OPEN);
        fy.setCurrent(true);
        fiscalYears.save(fy);

        LocalDate periodStart = start;
        for (int i = 1; i <= 12; i++) {
            LocalDate periodEnd = periodStart.plusMonths(1).minusDays(1);
            if (periodEnd.isAfter(end)) {
                periodEnd = end;
            }
            AccountingPeriod period = new AccountingPeriod();
            period.setOrganizationId(orgId);
            period.setFiscalYearId(fy.getId());
            period.setPeriodNumber(i);
            period.setName(periodStart.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " "
                    + periodStart.getYear());
            period.setStartDate(periodStart);
            period.setEndDate(periodEnd);
            period.setStatus(PeriodStatus.OPEN);
            periods.save(period);
            periodStart = periodEnd.plusDays(1);
            if (periodStart.isAfter(end)) {
                break;
            }
        }
        return fy;
    }
}
