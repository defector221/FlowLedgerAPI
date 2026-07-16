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

    public static AccountResponse toAccount(Account a) {
        return new AccountResponse(
                a.getId(),
                a.getOrganizationId(),
                a.getAccountCode(),
                a.getAccountName(),
                a.getDescription(),
                a.getAccountType(),
                a.getAccountSubType(),
                a.getParentAccountId(),
                a.getSystemAccountKey(),
                a.isSystemAccount(),
                a.isEditable(),
                a.isDeletable(),
                a.getStatus(),
                a.isActive(),
                a.isAllowManualPosting(),
                a.getOpeningDebit(),
                a.getOpeningCredit());
    }

    public static FiscalYearResponse toFiscalYear(FiscalYear f) {
        return new FiscalYearResponse(
                f.getId(), f.getName(), f.getStartDate(), f.getEndDate(), f.getStatus(), f.isCurrent());
    }

    public static PeriodResponse toPeriod(AccountingPeriod p) {
        return new PeriodResponse(
                p.getId(),
                p.getFiscalYearId(),
                p.getPeriodNumber(),
                p.getName(),
                p.getStartDate(),
                p.getEndDate(),
                p.getStatus());
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

    public static JournalResponse toJournal(JournalEntry entry, List<JournalEntryLine> lines, Map<UUID, Account> accounts) {
        List<JournalLineResponse> lineResponses = lines.stream()
                .map(l -> toLine(l, accounts.get(l.getAccountId())))
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
