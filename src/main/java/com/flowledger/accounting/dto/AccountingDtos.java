package com.flowledger.accounting.dto;

import com.flowledger.accounting.domain.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public final class AccountingDtos {
    private AccountingDtos() {}

    public record AccountRequest(
            @NotBlank String accountCode,
            @NotBlank String accountName,
            String description,
            @NotNull AccountType accountType,
            AccountSubType accountSubType,
            UUID parentAccountId,
            Boolean active,
            AccountStatus status,
            Boolean allowManualPosting,
            BigDecimal openingDebit,
            BigDecimal openingCredit) {}

    public record AccountResponse(
            UUID id,
            UUID organizationId,
            String accountCode,
            String accountName,
            String description,
            AccountType accountType,
            AccountSubType accountSubType,
            UUID parentAccountId,
            SystemAccountKey systemAccountKey,
            boolean systemAccount,
            boolean editable,
            boolean deletable,
            AccountStatus status,
            boolean active,
            boolean allowManualPosting,
            BigDecimal openingDebit,
            BigDecimal openingCredit) {}

    public record AccountTreeNode(
            UUID id,
            String accountCode,
            String accountName,
            String description,
            AccountType accountType,
            AccountSubType accountSubType,
            UUID parentAccountId,
            SystemAccountKey systemAccountKey,
            boolean systemAccount,
            boolean editable,
            boolean deletable,
            AccountStatus status,
            boolean active,
            boolean allowManualPosting,
            java.util.List<AccountTreeNode> children) {}

    public record FiscalYearRequest(@NotBlank String name, @NotNull LocalDate startDate, @NotNull LocalDate endDate) {}

    public record FiscalYearResponse(
            UUID id, String name, LocalDate startDate, LocalDate endDate, FiscalYearStatus status, boolean current) {}

    public record PeriodResponse(
            UUID id,
            UUID fiscalYearId,
            int periodNumber,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            PeriodStatus status) {}

    public record JournalLineRequest(
            @NotNull UUID accountId,
            String description,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            UUID customerId,
            UUID supplierId,
            String reference) {}

    public record JournalLineResponse(
            UUID id,
            UUID accountId,
            String accountCode,
            String accountName,
            int lineNumber,
            String description,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            UUID customerId,
            UUID supplierId,
            String reference) {}

    public record JournalRequest(
            @NotNull LocalDate entryDate,
            VoucherType voucherType,
            String description,
            String voucherNumber,
            @NotEmpty @Valid List<JournalLineRequest> lines) {}

    public record JournalResponse(
            UUID id,
            String entryNumber,
            LocalDate entryDate,
            LocalDate postingDate,
            VoucherType voucherType,
            String voucherNumber,
            String description,
            JournalStatus status,
            JournalSource source,
            String referenceType,
            UUID referenceId,
            UUID reversalOfId,
            UUID reversedById,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            OffsetDateTime postedAt,
            List<JournalLineResponse> lines) {}

    public record InitializeResponse(boolean initialized, int accountCount, FiscalYearResponse fiscalYear) {}

    public record LedgerLineResponse(
            UUID journalEntryId,
            String entryNumber,
            LocalDate entryDate,
            String description,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            BigDecimal runningBalance,
            UUID accountId,
            String accountCode,
            String accountName,
            UUID customerId,
            UUID supplierId) {}

    public record TrialBalanceRow(
            UUID accountId,
            String accountCode,
            String accountName,
            AccountType accountType,
            BigDecimal openingDebit,
            BigDecimal openingCredit,
            BigDecimal periodDebit,
            BigDecimal periodCredit,
            BigDecimal closingDebit,
            BigDecimal closingCredit) {}

    public record TrialBalanceResponse(
            LocalDate fromDate,
            LocalDate toDate,
            List<TrialBalanceRow> rows,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            boolean balanced) {}

    public record NamedAmount(String name, BigDecimal amount) {}

    public record ProfitAndLossResponse(
            LocalDate fromDate,
            LocalDate toDate,
            List<NamedAmount> income,
            List<NamedAmount> expenses,
            BigDecimal totalIncome,
            BigDecimal totalExpenses,
            BigDecimal netProfit) {}

    public record BalanceSheetResponse(
            LocalDate asOfDate,
            List<NamedAmount> assets,
            List<NamedAmount> liabilities,
            List<NamedAmount> equity,
            BigDecimal totalAssets,
            BigDecimal totalLiabilities,
            BigDecimal totalEquity,
            boolean balanced) {}

    public record GstSummaryResponse(
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal outputCgst,
            BigDecimal outputSgst,
            BigDecimal outputIgst,
            BigDecimal inputCgst,
            BigDecimal inputSgst,
            BigDecimal inputIgst,
            BigDecimal netPayable) {}

    public record IntegrityIssue(String code, String message, UUID journalEntryId) {}

    public record IntegrityCheckResponse(boolean healthy, List<IntegrityIssue> issues) {}

    public record GlLineResponse(
            UUID journalEntryId,
            String entryNumber,
            LocalDate entryDate,
            UUID accountId,
            String accountCode,
            String accountName,
            String description,
            BigDecimal debitAmount,
            BigDecimal creditAmount) {}

    public record DashboardSummaryResponse(
            BigDecimal totalReceivables,
            BigDecimal totalPayables,
            BigDecimal cashAndBank,
            BigDecimal netProfitMtd,
            long journalCountMtd,
            long unbalancedJournals) {}

    public record DayBookEntry(
            UUID id,
            String documentNumber,
            LocalDate documentDate,
            String documentType,
            String description,
            String source,
            BigDecimal totalDebit,
            BigDecimal totalCredit) {}

    public record DayBookResponse(
            LocalDate fromDate,
            LocalDate toDate,
            String sourceType,
            List<DayBookEntry> entries,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            long entryCount) {}

    public record CashBookLine(
            UUID journalEntryId,
            String entryNumber,
            LocalDate entryDate,
            UUID accountId,
            String accountCode,
            String accountName,
            String description,
            BigDecimal debitAmount,
            BigDecimal creditAmount,
            BigDecimal runningBalance) {}

    public record CashBookResponse(
            LocalDate fromDate,
            LocalDate toDate,
            String bookType,
            BigDecimal openingBalance,
            List<CashBookLine> lines,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            BigDecimal closingBalance) {}

    public record CashFlowSection(String name, List<NamedAmount> items, BigDecimal total) {}

    public record CashFlowResponse(
            LocalDate fromDate,
            LocalDate toDate,
            BigDecimal netProfit,
            CashFlowSection operating,
            CashFlowSection workingCapital,
            BigDecimal netCashFromOperations,
            BigDecimal openingCash,
            BigDecimal closingCash,
            BigDecimal netChangeInCash) {}
}
