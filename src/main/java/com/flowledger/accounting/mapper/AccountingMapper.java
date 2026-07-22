package com.flowledger.accounting.mapper;

import com.flowledger.accounting.dto.AccountingDtos.*;
import com.flowledger.accounting.entity.Account;
import com.flowledger.accounting.entity.AccountingPeriod;
import com.flowledger.accounting.entity.FiscalYear;
import com.flowledger.accounting.entity.JournalEntry;
import com.flowledger.accounting.entity.JournalEntryLine;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AccountingMapper {
    private AccountingMapper() {}

    public static AccountResponse toAccount(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getOrganizationId(),
                account.getAccountCode(),
                account.getAccountName(),
                account.getDescription(),
                account.getAccountType(),
                account.getAccountSubType(),
                account.getParentAccountId(),
                account.getSystemAccountKey(),
                account.isSystemAccount(),
                account.isEditable(),
                account.isDeletable(),
                account.getStatus(),
                account.isActive(),
                account.isAllowManualPosting(),
                account.getOpeningDebit(),
                account.getOpeningCredit());
    }

    public static FiscalYearResponse toFiscalYear(FiscalYear fiscalYear) {
        return new FiscalYearResponse(
                fiscalYear.getId(),
                fiscalYear.getName(),
                fiscalYear.getStartDate(),
                fiscalYear.getEndDate(),
                fiscalYear.getStatus(),
                fiscalYear.isCurrent());
    }

    public static PeriodResponse toPeriod(AccountingPeriod period) {
        return new PeriodResponse(
                period.getId(),
                period.getFiscalYearId(),
                period.getPeriodNumber(),
                period.getName(),
                period.getStartDate(),
                period.getEndDate(),
                period.getStatus());
    }

    public static JournalLineResponse toLine(JournalEntryLine line, Account account) {
        return new JournalLineResponse(
                line.getId(),
                line.getAccountId(),
                account != null ? account.getAccountCode() : null,
                account != null ? account.getAccountName() : null,
                line.getLineNumber(),
                line.getDescription(),
                line.getDebitAmount(),
                line.getCreditAmount(),
                line.getCustomerId(),
                line.getSupplierId(),
                line.getReference());
    }

    public static JournalResponse toJournal(
            JournalEntry entry, List<JournalEntryLine> lines, Map<UUID, Account> accounts) {
        List<JournalLineResponse> lineResponses = lines.stream()
                .map(line -> toLine(line, accounts.get(line.getAccountId())))
                .toList();
        return new JournalResponse(
                entry.getId(),
                entry.getEntryNumber(),
                entry.getEntryDate(),
                entry.getPostingDate(),
                entry.getVoucherType(),
                entry.getVoucherNumber(),
                entry.getDescription(),
                entry.getStatus(),
                entry.getSource(),
                entry.getReferenceType(),
                entry.getReferenceId(),
                entry.getReversalOfId(),
                entry.getReversedById(),
                entry.getTotalDebit(),
                entry.getTotalCredit(),
                entry.getPostedAt(),
                lineResponses);
    }
}
