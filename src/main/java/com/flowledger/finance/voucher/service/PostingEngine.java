package com.flowledger.finance.voucher.service;

import com.flowledger.accounting.domain.JournalSource;
import com.flowledger.accounting.domain.JournalStatus;
import com.flowledger.accounting.dto.AccountingDtos.JournalResponse;
import com.flowledger.accounting.entity.JournalEntry;
import com.flowledger.accounting.entity.JournalEntryLine;
import com.flowledger.accounting.repository.JournalEntryLineRepository;
import com.flowledger.accounting.repository.JournalEntryRepository;
import com.flowledger.accounting.service.AccountingPostingService;
import com.flowledger.accounting.service.JournalValidationService;
import com.flowledger.accounting.service.LedgerBalanceService;
import com.flowledger.accounting.util.AccountingMoney;
import com.flowledger.common.exception.ConflictException;
import com.flowledger.common.security.SecurityUtils;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.common.util.DocumentNumberService;
import com.flowledger.common.util.FinancialYearUtil;
import com.flowledger.finance.voucher.domain.VoucherStatus;
import com.flowledger.finance.voucher.domain.VoucherType;
import com.flowledger.finance.voucher.dto.VoucherDtos.PostingResult;
import com.flowledger.finance.voucher.entity.Voucher;
import com.flowledger.finance.voucher.entity.VoucherLine;
import com.flowledger.finance.voucher.event.VoucherPostedEvent;
import com.flowledger.finance.voucher.event.VoucherReversedEvent;
import com.flowledger.finance.voucher.repository.VoucherRepository;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.platform.event.DomainEventPublisher;
import com.flowledger.platform.history.service.DocumentHistoryService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PostingEngine {
    private final VoucherRepository vouchers;
    private final VoucherService voucherService;
    private final JournalValidationService validation;
    private final JournalEntryRepository journals;
    private final JournalEntryLineRepository journalLines;
    private final OrganizationRepository organizations;
    private final DocumentNumberService numbers;
    private final LedgerBalanceService ledgerBalances;
    private final AccountingPostingService accountingPosting;
    private final DomainEventPublisher events;
    private final DocumentHistoryService history;

    public PostingEngine(
            VoucherRepository vouchers,
            VoucherService voucherService,
            JournalValidationService validation,
            JournalEntryRepository journals,
            JournalEntryLineRepository journalLines,
            OrganizationRepository organizations,
            DocumentNumberService numbers,
            LedgerBalanceService ledgerBalances,
            AccountingPostingService accountingPosting,
            DomainEventPublisher events,
            DocumentHistoryService history) {
        this.vouchers = vouchers;
        this.voucherService = voucherService;
        this.validation = validation;
        this.journals = journals;
        this.journalLines = journalLines;
        this.organizations = organizations;
        this.numbers = numbers;
        this.ledgerBalances = ledgerBalances;
        this.accountingPosting = accountingPosting;
        this.events = events;
        this.history = history;
    }

    @Transactional
    public PostingResult post(UUID voucherId) {
        Voucher voucher = voucherService.loadEntity(voucherId);
        if (voucher.getStatus() != VoucherStatus.APPROVED) {
            throw new ConflictException("Only approved vouchers can be posted");
        }
        if (voucher.isPosted() || voucher.getJournalEntryId() != null) {
            throw new ConflictException("Voucher is already posted");
        }

        UUID org = voucher.getOrganizationId();
        List<JournalValidationService.LineInput> inputs = voucher.getLines().stream()
                .map(l -> new JournalValidationService.LineInput(l.getAccountId(), l.getDebit(), l.getCredit()))
                .toList();
        JournalSource journalSource = resolveJournalSource(voucher.getReferenceType());
        var result = validation.validate(org, voucher.getVoucherDate(), journalSource, inputs);

        JournalEntry entry = newJournal(
                org,
                voucher.getVoucherDate(),
                result,
                mapVoucherType(voucher.getVoucherType()),
                journalSource,
                voucher.getReferenceType() != null ? voucher.getReferenceType() : "VOUCHER",
                voucher.getReferenceId() != null ? voucher.getReferenceId() : voucher.getId(),
                voucher.getNarration(),
                voucher.getVoucherNumber(),
                JournalStatus.POSTED);
        List<JournalEntryLine> persisted = persistLines(org, entry, voucher.getLines());
        entry.setTotalDebit(result.totalDebit());
        entry.setTotalCredit(result.totalCredit());
        markPosted(entry);
        journals.save(entry);

        ledgerBalances.applyJournal(entry, persisted);

        voucher.setStatus(VoucherStatus.POSTED);
        voucher.setPosted(true);
        voucher.setPostedAt(OffsetDateTime.now());
        voucher.setJournalEntryId(entry.getId());
        try {
            voucher.setPostedBy(SecurityUtils.currentUserId());
        } catch (Exception ignored) {
            voucher.setPostedBy(TenantContext.userId().orElse(null));
        }
        vouchers.save(voucher);

        history.record("VOUCHER", voucher.getId(), "POSTED", "Voucher posted to ledger", null);
        events.publish(
                new VoucherPostedEvent(this, org, TenantContext.userId().orElse(null), voucher.getId(), entry.getId()));

        return new PostingResult(voucher.getId(), entry.getId(), VoucherStatus.POSTED, "Posted successfully");
    }

    @Transactional
    public PostingResult reverse(UUID voucherId) {
        Voucher voucher = voucherService.loadEntity(voucherId);
        if (voucher.getStatus() != VoucherStatus.POSTED || voucher.getJournalEntryId() == null) {
            throw new ConflictException("Only posted vouchers can be reversed");
        }

        UUID originalJournalId = voucher.getJournalEntryId();
        JournalEntry original = journals.findByIdAndOrganizationId(originalJournalId, voucher.getOrganizationId())
                .orElseThrow(() -> new ConflictException("Linked journal entry not found"));

        boolean alreadyReversed = original.getReversedById() != null || original.getStatus() == JournalStatus.REVERSED;
        UUID reversalJournalId;
        if (alreadyReversed) {
            reversalJournalId = original.getReversedById();
        } else {
            JournalResponse reversal = accountingPosting.reverseJournalEntry(originalJournalId);
            reversalJournalId = reversal.id();
            JournalEntry reversalEntry = journals.findByIdAndOrganizationId(
                            reversalJournalId, voucher.getOrganizationId())
                    .orElseThrow(() -> new ConflictException("Reversal journal entry not found"));
            List<JournalEntryLine> reversalLines =
                    journalLines.findByJournalEntryIdOrderByLineNumberAsc(reversalEntry.getId());
            ledgerBalances.applyJournal(reversalEntry, reversalLines);
        }

        voucher.setStatus(VoucherStatus.REVERSED);
        voucher.setPosted(false);
        vouchers.save(voucher);

        history.record("VOUCHER", voucher.getId(), "REVERSED", "Voucher reversed", null);
        events.publish(new VoucherReversedEvent(
                this,
                voucher.getOrganizationId(),
                TenantContext.userId().orElse(null),
                voucher.getId(),
                originalJournalId,
                reversalJournalId));

        return new PostingResult(voucher.getId(), reversalJournalId, VoucherStatus.REVERSED, "Reversed successfully");
    }

    private JournalEntry newJournal(
            UUID org,
            LocalDate entryDate,
            JournalValidationService.ValidationResult result,
            com.flowledger.accounting.domain.VoucherType voucherType,
            JournalSource source,
            String referenceType,
            UUID referenceId,
            String description,
            String voucherNumber,
            JournalStatus status) {
        Organization organization = organizations.findById(org).orElseThrow();
        String entryNumber = nextJournalEntryNumber(org, organization, entryDate);
        JournalEntry entry = new JournalEntry();
        entry.setOrganizationId(org);
        entry.setFiscalYearId(result.period().getFiscalYearId());
        entry.setAccountingPeriodId(result.period().getId());
        entry.setEntryNumber(entryNumber);
        entry.setEntryDate(entryDate);
        entry.setVoucherType(voucherType);
        entry.setSource(source);
        entry.setReferenceType(referenceType);
        entry.setReferenceId(referenceId);
        entry.setDescription(description);
        entry.setVoucherNumber(voucherNumber);
        entry.setStatus(status);
        entry.setTotalDebit(result.totalDebit());
        entry.setTotalCredit(result.totalCredit());
        return journals.save(entry);
    }

    private String nextJournalEntryNumber(UUID org, Organization organization, LocalDate entryDate) {
        String fy = FinancialYearUtil.financialYear(entryDate, organization.getFinancialYearStart());
        long maxExisting = journals.maxEntrySequence(org, "JV/" + fy + "/%");
        numbers.ensureNextAtLeast(
                org, "JOURNAL", "JV", organization.getFinancialYearStart(), entryDate, maxExisting + 1);
        for (int attempt = 0; attempt < 8; attempt++) {
            String candidate = numbers.next(
                    org, "JOURNAL", "JV", "{PREFIX}/{FY}/{SEQ:6}", organization.getFinancialYearStart(), entryDate);
            if (!journals.existsByOrganizationIdAndEntryNumber(org, candidate)) {
                return candidate;
            }
        }
        throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Unable to allocate a unique journal entry number. Please retry.");
    }

    private List<JournalEntryLine> persistLines(UUID org, JournalEntry entry, List<VoucherLine> lines) {
        List<JournalEntryLine> saved = new ArrayList<>();
        int n = 1;
        for (VoucherLine req : lines) {
            BigDecimal debit = AccountingMoney.normalize(req.getDebit());
            BigDecimal credit = AccountingMoney.normalize(req.getCredit());
            if (AccountingMoney.isZero(debit) && AccountingMoney.isZero(credit)) {
                continue;
            }
            JournalEntryLine line = new JournalEntryLine();
            line.setOrganizationId(org);
            line.setJournalEntryId(entry.getId());
            line.setAccountId(req.getAccountId());
            line.setLineNumber(n++);
            line.setDescription(req.getDescription());
            line.setDebitAmount(debit);
            line.setCreditAmount(credit);
            line.setCostCenterId(req.getCostCenterId());
            line.setReference(req.getInventoryReference());
            saved.add(journalLines.save(line));
        }
        return saved;
    }

    private void markPosted(JournalEntry entry) {
        entry.setStatus(JournalStatus.POSTED);
        entry.setPostingDate(entry.getEntryDate());
        entry.setPostedAt(OffsetDateTime.now());
        try {
            entry.setPostedBy(SecurityUtils.currentUserId());
        } catch (Exception ignored) {
            entry.setPostedBy(null);
        }
    }

    static JournalSource resolveJournalSource(String referenceType) {
        if (referenceType == null || referenceType.isBlank()) {
            return JournalSource.VOUCHER;
        }
        return switch (referenceType) {
            case "SALES_INVOICE" -> JournalSource.SALES_INVOICE;
            case "PURCHASE_INVOICE" -> JournalSource.PURCHASE_INVOICE;
            case "SALES_RETURN" -> JournalSource.SALES_RETURN;
            case "PURCHASE_RETURN" -> JournalSource.PURCHASE_RETURN;
            case "CUSTOMER_RECEIPT" -> JournalSource.CUSTOMER_RECEIPT;
            case "SUPPLIER_PAYMENT" -> JournalSource.SUPPLIER_PAYMENT;
            case "CREDIT_NOTE" -> JournalSource.CREDIT_NOTE;
            case "DEBIT_NOTE" -> JournalSource.DEBIT_NOTE;
            case "CONTRA", "BANK_TRANSFER" -> JournalSource.CONTRA;
            case "OPENING_BALANCE" -> JournalSource.OPENING_BALANCE;
            case "STOCK_ADJUSTMENT", "STOCK_TRANSFER", "ADJUSTMENT" -> JournalSource.STOCK_ADJUSTMENT;
            default -> JournalSource.VOUCHER;
        };
    }

    static com.flowledger.accounting.domain.VoucherType mapVoucherType(VoucherType type) {
        if (type == null) {
            return com.flowledger.accounting.domain.VoucherType.JOURNAL;
        }
        return switch (type) {
            case SALES -> com.flowledger.accounting.domain.VoucherType.SALES;
            case PURCHASE -> com.flowledger.accounting.domain.VoucherType.PURCHASE;
            case PAYMENT -> com.flowledger.accounting.domain.VoucherType.PAYMENT;
            case RECEIPT -> com.flowledger.accounting.domain.VoucherType.RECEIPT;
            case JOURNAL -> com.flowledger.accounting.domain.VoucherType.JOURNAL;
            case CONTRA -> com.flowledger.accounting.domain.VoucherType.CONTRA;
            case CREDIT_NOTE -> com.flowledger.accounting.domain.VoucherType.CREDIT_NOTE;
            case DEBIT_NOTE -> com.flowledger.accounting.domain.VoucherType.DEBIT_NOTE;
            case EXPENSE -> com.flowledger.accounting.domain.VoucherType.EXPENSE;
            case OPENING_BALANCE -> com.flowledger.accounting.domain.VoucherType.OPENING_BALANCE;
            case PAYROLL -> com.flowledger.accounting.domain.VoucherType.PAYROLL;
            case ASSET_PURCHASE -> com.flowledger.accounting.domain.VoucherType.ASSET_PURCHASE;
            case DEPRECIATION -> com.flowledger.accounting.domain.VoucherType.DEPRECIATION;
            case STOCK_TRANSFER, STOCK_ADJUSTMENT, PRODUCTION_ISSUE, PRODUCTION_RECEIPT ->
                com.flowledger.accounting.domain.VoucherType.JOURNAL;
        };
    }
}
