package com.flowledger.accounting.service;

import com.flowledger.accounting.bootstrap.ChartOfAccountsTemplate;
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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bootstraps a complete default Chart of Accounts for a new organization.
 * Idempotent: safe to call from org registration, onboarding, and lazy-init paths.
 */
@Service
public class ChartOfAccountsBootstrapService {
    private final AccountRepository accounts;
    private final FiscalYearRepository fiscalYears;
    private final AccountingPeriodRepository periods;

    public ChartOfAccountsBootstrapService(
            AccountRepository accounts, FiscalYearRepository fiscalYears, AccountingPeriodRepository periods) {
        this.accounts = accounts;
        this.fiscalYears = fiscalYears;
        this.periods = periods;
    }

    @Transactional
    public void bootstrapOrganization(UUID organizationId) {
        bootstrapOrganization(organizationId, "04-01");
    }

    @Transactional
    public void bootstrapOrganization(UUID organizationId, String financialYearStart) {
        if (!accounts.existsByOrganizationIdAndSystemAccountKeyIsNotNull(organizationId)) {
            seedAccountsFromTemplate(organizationId);
        }
        if (fiscalYears.findByOrganizationIdAndCurrentTrue(organizationId).isEmpty()) {
            ensureCurrentFiscalYear(organizationId, financialYearStart == null ? "04-01" : financialYearStart);
        }
    }

    void seedAccountsFromTemplate(UUID orgId) {
        Map<String, UUID> idByCode = new HashMap<>();
        for (ChartOfAccountsTemplate.Node node : ChartOfAccountsTemplate.NODES) {
            if (accounts.findByOrganizationIdAndAccountCode(orgId, node.code()).isPresent()) {
                accounts.findByOrganizationIdAndAccountCode(orgId, node.code()).ifPresent(a -> idByCode.put(node.code(), a.getId()));
                continue;
            }
            Account account = new Account();
            account.setOrganizationId(orgId);
            account.setAccountCode(node.code());
            account.setAccountName(node.name());
            account.setAccountType(node.accountType());
            account.setAccountSubType(node.accountSubType());
            account.setSystemAccountKey(node.systemAccountKey());
            account.setSystemAccount(true);
            account.setEditable(node.groupHeader() ? false : true);
            account.setDeletable(false);
            account.setStatus(AccountStatus.ACTIVE);
            account.setActive(true);
            account.setAllowManualPosting(node.allowManualPosting());
            account.setOpeningDebit(AccountingMoney.normalize(BigDecimal.ZERO));
            account.setOpeningCredit(AccountingMoney.normalize(BigDecimal.ZERO));
            if (node.parentCode() != null) {
                UUID parentId = idByCode.get(node.parentCode());
                if (parentId == null) {
                    parentId = accounts.findByOrganizationIdAndAccountCode(orgId, node.parentCode())
                            .map(Account::getId)
                            .orElse(null);
                }
                account.setParentAccountId(parentId);
            }
            Account saved = accounts.save(account);
            idByCode.put(node.code(), saved.getId());
        }
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
