package com.flowledger.accounting.controller;

import com.flowledger.accounting.domain.JournalStatus;
import com.flowledger.accounting.dto.AccountingDtos.*;
import com.flowledger.accounting.service.AccountService;
import com.flowledger.accounting.service.AccountingPostingService;
import com.flowledger.accounting.service.FiscalYearService;
import com.flowledger.accounting.service.LedgerService;
import com.flowledger.accounting.service.reporting.AccountingReportService;
import com.flowledger.common.dto.ApiResponse;
import com.flowledger.common.dto.PageResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounting")
@PreAuthorize("hasAnyRole('ORGANIZATION_ADMIN', 'ACCOUNTANT')")
public class AccountingController {
    private final AccountingPostingService posting;
    private final AccountService accounts;
    private final FiscalYearService fiscalYears;
    private final LedgerService ledgers;
    private final AccountingReportService reports;

    public AccountingController(
            AccountingPostingService posting,
            AccountService accounts,
            FiscalYearService fiscalYears,
            LedgerService ledgers,
            AccountingReportService reports) {
        this.posting = posting;
        this.accounts = accounts;
        this.fiscalYears = fiscalYears;
        this.ledgers = ledgers;
        this.reports = reports;
    }

    @GetMapping("/accounts")
    public ApiResponse<List<AccountResponse>> listAccounts() {
        return ApiResponse.of(accounts.list());
    }

    @GetMapping("/accounts/tree")
    public ApiResponse<List<AccountTreeNode>> accountTree() {
        return ApiResponse.of(accounts.tree());
    }

    @GetMapping("/accounts/{id}")
    public ApiResponse<AccountResponse> getAccount(@PathVariable UUID id) {
        return ApiResponse.of(accounts.get(id));
    }

    @PostMapping("/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AccountResponse> createAccount(@Valid @RequestBody AccountRequest request) {
        return ApiResponse.of(accounts.create(request));
    }

    @PutMapping("/accounts/{id}")
    public ApiResponse<AccountResponse> updateAccount(
            @PathVariable UUID id, @Valid @RequestBody AccountRequest request) {
        return ApiResponse.of(accounts.update(id, request));
    }

    @DeleteMapping("/accounts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@PathVariable UUID id) {
        accounts.delete(id);
    }

    @GetMapping("/fiscal-years")
    public ApiResponse<List<FiscalYearResponse>> listFiscalYears() {
        return ApiResponse.of(fiscalYears.list());
    }

    @PostMapping("/fiscal-years")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FiscalYearResponse> createFiscalYear(@Valid @RequestBody FiscalYearRequest request) {
        return ApiResponse.of(fiscalYears.create(request));
    }

    @GetMapping("/fiscal-years/{id}/periods")
    public ApiResponse<List<PeriodResponse>> listPeriods(@PathVariable UUID id) {
        return ApiResponse.of(fiscalYears.periods(id));
    }

    @GetMapping("/journals")
    public ApiResponse<PageResponse<JournalResponse>> listJournals(
            @RequestParam(required = false) JournalStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.of(PageResponse.from(posting.listJournals(status, from, to, pageable)));
    }

    @GetMapping("/journals/{id}")
    public ApiResponse<JournalResponse> getJournal(@PathVariable UUID id) {
        return ApiResponse.of(posting.getJournal(id));
    }

    @PostMapping("/journals")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<JournalResponse> createJournal(@Valid @RequestBody JournalRequest request) {
        return ApiResponse.of(posting.createDraft(request));
    }

    @PutMapping("/journals/{id}")
    public ApiResponse<JournalResponse> updateJournal(
            @PathVariable UUID id, @Valid @RequestBody JournalRequest request) {
        return ApiResponse.of(posting.updateDraft(id, request));
    }

    @PostMapping("/journals/{id}/post")
    public ApiResponse<JournalResponse> postJournal(@PathVariable UUID id) {
        return ApiResponse.of(posting.postJournal(id));
    }

    @PostMapping("/journals/{id}/reverse")
    public ApiResponse<JournalResponse> reverseJournal(@PathVariable UUID id) {
        return ApiResponse.of(posting.reverseJournalEntry(id));
    }

    @GetMapping("/ledgers/accounts/{id}")
    public ApiResponse<List<LedgerLineResponse>> accountLedger(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.of(ledgers.accountLedger(id, from, to));
    }

    @GetMapping("/ledgers/customers/{id}")
    public ApiResponse<List<LedgerLineResponse>> customerLedger(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.of(ledgers.customerLedger(id, from, to));
    }

    @GetMapping("/ledgers/suppliers/{id}")
    public ApiResponse<List<LedgerLineResponse>> supplierLedger(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.of(ledgers.supplierLedger(id, from, to));
    }

    @GetMapping("/reports/trial-balance")
    public ApiResponse<TrialBalanceResponse> trialBalance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.of(reports.trialBalance(from, to));
    }

    @GetMapping("/reports/profit-loss")
    public ApiResponse<ProfitAndLossResponse> profitAndLoss(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.of(reports.profitAndLoss(from, to));
    }

    @GetMapping("/reports/balance-sheet")
    public ApiResponse<BalanceSheetResponse> balanceSheet(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ApiResponse.of(reports.balanceSheet(asOf != null ? asOf : LocalDate.now()));
    }

    @GetMapping("/reports/gst-summary")
    public ApiResponse<GstSummaryResponse> gstSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.of(reports.gstSummary(from, to));
    }

    @GetMapping("/reports/general-ledger")
    public ApiResponse<List<GlLineResponse>> generalLedger(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.of(reports.generalLedger(from, to));
    }

    @GetMapping("/reports/integrity-check")
    @PreAuthorize("hasRole('ORGANIZATION_ADMIN')")
    public ApiResponse<IntegrityCheckResponse> integrityCheck() {
        return ApiResponse.of(reports.integrityCheck());
    }

    @GetMapping("/dashboard")
    public ApiResponse<DashboardSummaryResponse> dashboard() {
        return ApiResponse.of(reports.dashboard());
    }
}
