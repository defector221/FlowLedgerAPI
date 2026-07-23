package com.flowledger.accounting.service;

import com.flowledger.accounting.domain.*;
import com.flowledger.accounting.dto.AccountingDtos.*;
import com.flowledger.accounting.entity.Account;
import com.flowledger.accounting.entity.JournalEntry;
import com.flowledger.accounting.entity.JournalEntryLine;
import com.flowledger.accounting.mapper.AccountingMapper;
import com.flowledger.accounting.repository.AccountRepository;
import com.flowledger.accounting.repository.FiscalYearRepository;
import com.flowledger.accounting.repository.JournalEntryLineRepository;
import com.flowledger.accounting.repository.JournalEntryRepository;
import com.flowledger.accounting.util.AccountingMoney;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.exception.ConflictException;
import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.common.security.SecurityUtils;
import com.flowledger.common.tenant.TenantContext;
import com.flowledger.common.util.DocumentNumberService;
import com.flowledger.common.util.FinancialYearUtil;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import com.flowledger.payment.entity.Payment;
import com.flowledger.purchase.entity.PurchaseInvoice;
import com.flowledger.purchase.entity.PurchaseReturn;
import com.flowledger.sales.entity.CreditNote;
import com.flowledger.sales.entity.SalesInvoice;
import com.flowledger.sales.entity.SalesReturn;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountingPostingService {
    private final JournalEntryRepository journals;
    private final JournalEntryLineRepository lines;
    private final AccountRepository accounts;
    private final FiscalYearRepository fiscalYears;
    private final JournalValidationService validation;
    private final DocumentNumberService numbers;
    private final OrganizationRepository organizations;
    private final ChartOfAccountsBootstrapService bootstrap;

    public AccountingPostingService(
            JournalEntryRepository journals,
            JournalEntryLineRepository lines,
            AccountRepository accounts,
            FiscalYearRepository fiscalYears,
            JournalValidationService validation,
            DocumentNumberService numbers,
            OrganizationRepository organizations,
            ChartOfAccountsBootstrapService bootstrap) {
        this.journals = journals;
        this.lines = lines;
        this.accounts = accounts;
        this.fiscalYears = fiscalYears;
        this.validation = validation;
        this.numbers = numbers;
        this.organizations = organizations;
        this.bootstrap = bootstrap;
    }

    @Transactional
    public InitializeResponse initialize() {
        UUID orgId = TenantContext.getOrganizationId();
        Organization org = organizations
                .findById(orgId)
                .orElseThrow(() -> new ResourceNotFoundException("Organization not found"));
        bootstrap.bootstrapOrganization(orgId, org.getFinancialYearStart());
        int count = accounts.findByOrganizationIdOrderByAccountCodeAsc(orgId).size();
        var fy = fiscalYears
                .findByOrganizationIdAndCurrentTrue(orgId)
                .map(AccountingMapper::toFiscalYear)
                .orElse(null);
        return new InitializeResponse(true, count, fy);
    }

    @Transactional(readOnly = true)
    public Page<JournalResponse> listJournals(JournalStatus status, LocalDate from, LocalDate to, Pageable pageable) {
        UUID org = TenantContext.getOrganizationId();
        return journals.search(org, status, from, to, pageable).map(j -> toResponse(j, false));
    }

    @Transactional(readOnly = true)
    public JournalResponse getJournal(UUID id) {
        return toResponse(loadJournal(id), true);
    }

    @Transactional
    public JournalResponse createDraft(JournalRequest request) {
        UUID org = TenantContext.getOrganizationId();
        ensureInitialized(org);
        var result = validation.validate(org, request.entryDate(), JournalSource.MANUAL, toLineInputs(request.lines()));
        JournalEntry entry = newJournal(
                org,
                request.entryDate(),
                result,
                request.voucherType() == null ? VoucherType.JOURNAL : request.voucherType(),
                JournalSource.MANUAL,
                null,
                null,
                request.description(),
                request.voucherNumber(),
                JournalStatus.DRAFT);
        persistLines(org, entry, request.lines());
        entry.setTotalDebit(result.totalDebit());
        entry.setTotalCredit(result.totalCredit());
        return toResponse(journals.save(entry), true);
    }

    @Transactional
    public JournalResponse updateDraft(UUID id, JournalRequest request) {
        JournalEntry entry = loadJournal(id);
        if (entry.getStatus() != JournalStatus.DRAFT) {
            throw new ConflictException("Only draft journals can be updated");
        }
        if (entry.getSource() != JournalSource.MANUAL) {
            throw new ConflictException("System-generated journals cannot be edited");
        }
        UUID org = entry.getOrganizationId();
        var result = validation.validate(org, request.entryDate(), JournalSource.MANUAL, toLineInputs(request.lines()));
        lines.deleteByJournalEntryId(entry.getId());
        entry.setEntryDate(request.entryDate());
        entry.setAccountingPeriodId(result.period().getId());
        entry.setFiscalYearId(result.period().getFiscalYearId());
        entry.setVoucherType(request.voucherType() == null ? VoucherType.JOURNAL : request.voucherType());
        entry.setDescription(request.description());
        entry.setVoucherNumber(request.voucherNumber());
        entry.setTotalDebit(result.totalDebit());
        entry.setTotalCredit(result.totalCredit());
        persistLines(org, entry, request.lines());
        return toResponse(journals.save(entry), true);
    }

    @Transactional
    public JournalResponse postJournal(UUID id) {
        JournalEntry entry = loadJournal(id);
        if (entry.getStatus() != JournalStatus.DRAFT) {
            throw new ConflictException("Only draft journals can be posted");
        }
        List<JournalEntryLine> existing = lines.findByJournalEntryIdOrderByLineNumberAsc(entry.getId());
        var result = validation.validate(
                entry.getOrganizationId(),
                entry.getEntryDate(),
                entry.getSource(),
                existing.stream()
                        .map(l -> new JournalValidationService.LineInput(
                                l.getAccountId(), l.getDebitAmount(), l.getCreditAmount()))
                        .toList());
        entry.setAccountingPeriodId(result.period().getId());
        entry.setFiscalYearId(result.period().getFiscalYearId());
        entry.setTotalDebit(result.totalDebit());
        entry.setTotalCredit(result.totalCredit());
        markPosted(entry);
        return toResponse(journals.save(entry), true);
    }

    @Transactional
    public JournalResponse reverseJournalEntry(UUID id) {
        JournalEntry original = loadJournal(id);
        if (original.getStatus() != JournalStatus.POSTED) {
            throw new ConflictException("Only posted journals can be reversed");
        }
        if (original.getReversedById() != null) {
            return toResponse(loadJournal(original.getReversedById()), true);
        }
        List<JournalEntryLine> originalLines = lines.findByJournalEntryIdOrderByLineNumberAsc(original.getId());
        List<JournalLineRequest> reversalLines = originalLines.stream()
                .map(l -> new JournalLineRequest(
                        l.getAccountId(),
                        "Reversal: " + (l.getDescription() == null ? "" : l.getDescription()),
                        l.getCreditAmount(),
                        l.getDebitAmount(),
                        l.getCustomerId(),
                        l.getSupplierId(),
                        l.getReference()))
                .toList();
        LocalDate date = LocalDate.now();
        var result = validation.validate(
                original.getOrganizationId(), date, JournalSource.SYSTEM, toLineInputs(reversalLines));
        JournalEntry reversal = newJournal(
                original.getOrganizationId(),
                date,
                result,
                original.getVoucherType(),
                JournalSource.SYSTEM,
                "REVERSAL",
                original.getId(),
                "Reversal of " + original.getEntryNumber(),
                null,
                JournalStatus.POSTED);
        reversal.setReversalOfId(original.getId());
        persistLines(original.getOrganizationId(), reversal, reversalLines);
        reversal.setTotalDebit(result.totalDebit());
        reversal.setTotalCredit(result.totalCredit());
        markPosted(reversal);
        journals.save(reversal);
        original.setStatus(JournalStatus.REVERSED);
        original.setReversedById(reversal.getId());
        journals.save(original);
        return toResponse(reversal, true);
    }

    @Transactional
    public JournalEntry postSalesInvoice(SalesInvoice invoice) {
        if (invoice.getAccountingStatus() == AccountingStatus.POSTED && invoice.getPostedJournalEntryId() != null) {
            return journals.findById(invoice.getPostedJournalEntryId()).orElse(null);
        }
        UUID org = invoice.getOrganizationId();
        ensureInitialized(org);
        var existing =
                journals.findByOrganizationIdAndSourceAndReferenceId(org, JournalSource.SALES_INVOICE, invoice.getId());
        if (existing.isPresent()) {
            linkDocument(invoice, existing.get());
            return existing.get();
        }
        List<JournalLineRequest> built = new ArrayList<>();
        built.add(debit(
                system(org, SystemAccountKey.ACCOUNTS_RECEIVABLE),
                invoice.getGrandTotal(),
                "AR " + invoice.getInvoiceNumber(),
                invoice.getCustomerId(),
                null));
        BigDecimal salesAmount = AccountingMoney.normalize(
                invoice.getTaxableAmount() != null && invoice.getTaxableAmount().signum() > 0
                        ? invoice.getTaxableAmount()
                        : invoice.getSubtotal());
        if (AccountingMoney.isPositive(salesAmount)) {
            built.add(credit(
                    system(org, SystemAccountKey.SALES),
                    salesAmount,
                    "Sales " + invoice.getInvoiceNumber(),
                    null,
                    null));
        }
        addTaxCredit(built, org, SystemAccountKey.OUTPUT_CGST, invoice.getCgstTotal(), invoice.getInvoiceNumber());
        addTaxCredit(built, org, SystemAccountKey.OUTPUT_SGST, invoice.getSgstTotal(), invoice.getInvoiceNumber());
        addTaxCredit(built, org, SystemAccountKey.OUTPUT_IGST, invoice.getIgstTotal(), invoice.getInvoiceNumber());
        addRoundOff(built, org, invoice.getRoundOff(), invoice.getInvoiceNumber());
        balanceAgainstAr(built, org, invoice.getCustomerId());

        JournalEntry entry = postAuto(
                org,
                invoice.getInvoiceDate() != null ? invoice.getInvoiceDate() : LocalDate.now(),
                VoucherType.SALES,
                JournalSource.SALES_INVOICE,
                "SALES_INVOICE",
                invoice.getId(),
                "Sales invoice " + invoice.getInvoiceNumber(),
                invoice.getInvoiceNumber(),
                built);
        linkDocument(invoice, entry);
        return entry;
    }

    @Transactional
    public JournalEntry postPurchaseInvoice(PurchaseInvoice invoice) {
        if (invoice.getAccountingStatus() == AccountingStatus.POSTED && invoice.getPostedJournalEntryId() != null) {
            return journals.findById(invoice.getPostedJournalEntryId()).orElse(null);
        }
        UUID org = invoice.getOrganizationId();
        ensureInitialized(org);
        var existing = journals.findByOrganizationIdAndSourceAndReferenceId(
                org, JournalSource.PURCHASE_INVOICE, invoice.getId());
        if (existing.isPresent()) {
            linkPurchase(invoice, existing.get());
            return existing.get();
        }
        List<JournalLineRequest> built = new ArrayList<>();
        BigDecimal purchaseAmount = AccountingMoney.normalize(
                invoice.getTaxableAmount() != null && invoice.getTaxableAmount().signum() > 0
                        ? invoice.getTaxableAmount()
                        : invoice.getSubtotal());
        if (AccountingMoney.isPositive(purchaseAmount)) {
            built.add(debit(
                    system(org, SystemAccountKey.PURCHASE),
                    purchaseAmount,
                    "Purchase " + invoice.getInvoiceNumber(),
                    null,
                    null));
        }
        addTaxDebit(built, org, SystemAccountKey.INPUT_CGST, invoice.getCgstTotal(), invoice.getInvoiceNumber());
        addTaxDebit(built, org, SystemAccountKey.INPUT_SGST, invoice.getSgstTotal(), invoice.getInvoiceNumber());
        addTaxDebit(built, org, SystemAccountKey.INPUT_IGST, invoice.getIgstTotal(), invoice.getInvoiceNumber());
        built.add(credit(
                system(org, SystemAccountKey.ACCOUNTS_PAYABLE),
                invoice.getGrandTotal(),
                "AP " + invoice.getInvoiceNumber(),
                null,
                invoice.getSupplierId()));
        addRoundOff(built, org, invoice.getRoundOff(), invoice.getInvoiceNumber());
        balanceAgainstAp(built, org, invoice.getSupplierId());

        JournalEntry entry = postAuto(
                org,
                invoice.getInvoiceDate() != null ? invoice.getInvoiceDate() : LocalDate.now(),
                VoucherType.PURCHASE,
                JournalSource.PURCHASE_INVOICE,
                "PURCHASE_INVOICE",
                invoice.getId(),
                "Purchase invoice " + invoice.getInvoiceNumber(),
                invoice.getInvoiceNumber(),
                built);
        linkPurchase(invoice, entry);
        return entry;
    }

    @Transactional
    public JournalEntry postPayment(Payment payment) {
        if (payment.getAccountingStatus() == AccountingStatus.POSTED && payment.getPostedJournalEntryId() != null) {
            return journals.findById(payment.getPostedJournalEntryId()).orElse(null);
        }
        UUID org = payment.getOrganizationId();
        ensureInitialized(org);
        JournalSource source = payment.getPaymentType() == Payment.Type.RECEIPT
                ? JournalSource.CUSTOMER_RECEIPT
                : JournalSource.SUPPLIER_PAYMENT;
        var existing = journals.findByOrganizationIdAndSourceAndReferenceId(org, source, payment.getId());
        if (existing.isPresent()) {
            linkPayment(payment, existing.get());
            return existing.get();
        }
        UUID cashBank = system(org, SystemAccountKey.BANK);
        List<JournalLineRequest> built = new ArrayList<>();
        if (payment.getPaymentType() == Payment.Type.RECEIPT) {
            built.add(debit(cashBank, payment.getAmount(), "Receipt " + payment.getPaymentNumber(), null, null));
            built.add(credit(
                    system(org, SystemAccountKey.ACCOUNTS_RECEIVABLE),
                    payment.getAmount(),
                    "Receipt " + payment.getPaymentNumber(),
                    payment.getCustomerId(),
                    null));
        } else {
            built.add(debit(
                    system(org, SystemAccountKey.ACCOUNTS_PAYABLE),
                    payment.getAmount(),
                    "Payment " + payment.getPaymentNumber(),
                    null,
                    payment.getSupplierId()));
            built.add(credit(cashBank, payment.getAmount(), "Payment " + payment.getPaymentNumber(), null, null));
        }
        JournalEntry entry = postAuto(
                org,
                payment.getPaymentDate() != null ? payment.getPaymentDate() : LocalDate.now(),
                payment.getPaymentType() == Payment.Type.RECEIPT ? VoucherType.RECEIPT : VoucherType.PAYMENT,
                source,
                source.name(),
                payment.getId(),
                (payment.getPaymentType() == Payment.Type.RECEIPT ? "Receipt " : "Payment ")
                        + payment.getPaymentNumber(),
                payment.getPaymentNumber(),
                built);
        linkPayment(payment, entry);
        return entry;
    }

    @Transactional
    public JournalEntry postSalesReturn(SalesReturn salesReturn) {
        UUID org = salesReturn.getOrganizationId();
        ensureInitialized(org);
        var existing = journals.findByOrganizationIdAndSourceAndReferenceId(
                org, JournalSource.SALES_RETURN, salesReturn.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        List<JournalLineRequest> built = List.of(
                debit(
                        system(org, SystemAccountKey.SALES),
                        salesReturn.getGrandTotal(),
                        "Sales return " + salesReturn.getReturnNumber(),
                        null,
                        null),
                credit(
                        system(org, SystemAccountKey.ACCOUNTS_RECEIVABLE),
                        salesReturn.getGrandTotal(),
                        "Sales return " + salesReturn.getReturnNumber(),
                        salesReturn.getCustomerId(),
                        null));
        return postAuto(
                org,
                salesReturn.getReturnDate() != null ? salesReturn.getReturnDate() : LocalDate.now(),
                VoucherType.CREDIT_NOTE,
                JournalSource.SALES_RETURN,
                "SALES_RETURN",
                salesReturn.getId(),
                "Sales return " + salesReturn.getReturnNumber(),
                salesReturn.getReturnNumber(),
                built);
    }

    @Transactional
    public JournalEntry postCreditNote(CreditNote creditNote) {
        UUID org = creditNote.getOrganizationId();
        ensureInitialized(org);
        var existing = journals.findByOrganizationIdAndSourceAndReferenceId(
                org, JournalSource.CREDIT_NOTE, creditNote.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        List<JournalLineRequest> built = List.of(
                debit(
                        system(org, SystemAccountKey.SALES),
                        creditNote.getAmount(),
                        "Credit note " + creditNote.getCreditNoteNumber(),
                        null,
                        null),
                credit(
                        system(org, SystemAccountKey.ACCOUNTS_RECEIVABLE),
                        creditNote.getAmount(),
                        "Credit note " + creditNote.getCreditNoteNumber(),
                        creditNote.getCustomerId(),
                        null));
        return postAuto(
                org,
                creditNote.getCreditNoteDate() != null ? creditNote.getCreditNoteDate() : LocalDate.now(),
                VoucherType.CREDIT_NOTE,
                JournalSource.CREDIT_NOTE,
                "CREDIT_NOTE",
                creditNote.getId(),
                "Credit note " + creditNote.getCreditNoteNumber(),
                creditNote.getCreditNoteNumber(),
                built);
    }

    @Transactional
    public JournalEntry postPurchaseReturn(PurchaseReturn purchaseReturn) {
        UUID org = purchaseReturn.getOrganizationId();
        ensureInitialized(org);
        var existing = journals.findByOrganizationIdAndSourceAndReferenceId(
                org, JournalSource.PURCHASE_RETURN, purchaseReturn.getId());
        if (existing.isPresent()) {
            return existing.get();
        }
        List<JournalLineRequest> built = List.of(
                debit(
                        system(org, SystemAccountKey.ACCOUNTS_PAYABLE),
                        purchaseReturn.getGrandTotal(),
                        "Purchase return " + purchaseReturn.getReturnNumber(),
                        null,
                        purchaseReturn.getSupplierId()),
                credit(
                        system(org, SystemAccountKey.PURCHASE),
                        purchaseReturn.getGrandTotal(),
                        "Purchase return " + purchaseReturn.getReturnNumber(),
                        null,
                        null));
        return postAuto(
                org,
                purchaseReturn.getReturnDate() != null ? purchaseReturn.getReturnDate() : LocalDate.now(),
                VoucherType.DEBIT_NOTE,
                JournalSource.PURCHASE_RETURN,
                "PURCHASE_RETURN",
                purchaseReturn.getId(),
                "Purchase return " + purchaseReturn.getReturnNumber(),
                purchaseReturn.getReturnNumber(),
                built);
    }

    @Transactional
    public void reverseDocumentJournal(UUID organizationId, JournalSource source, UUID referenceId) {
        journals.findByOrganizationIdAndSourceAndReferenceId(organizationId, source, referenceId)
                .ifPresent(entry -> {
                    if (entry.getStatus() == JournalStatus.POSTED) {
                        reverseJournalEntry(entry.getId());
                    }
                });
    }

    private JournalEntry postAuto(
            UUID org,
            LocalDate entryDate,
            VoucherType voucherType,
            JournalSource source,
            String referenceType,
            UUID referenceId,
            String description,
            String voucherNumber,
            List<JournalLineRequest> built) {
        var result = validation.validate(org, entryDate, source, toLineInputs(built));
        JournalEntry entry = newJournal(
                org,
                entryDate,
                result,
                voucherType,
                source,
                referenceType,
                referenceId,
                description,
                voucherNumber,
                JournalStatus.POSTED);
        persistLines(org, entry, built);
        entry.setTotalDebit(result.totalDebit());
        entry.setTotalCredit(result.totalCredit());
        markPosted(entry);
        return journals.save(entry);
    }

    private JournalEntry newJournal(
            UUID org,
            LocalDate entryDate,
            JournalValidationService.ValidationResult result,
            VoucherType voucherType,
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

    private void persistLines(UUID org, JournalEntry entry, List<JournalLineRequest> requests) {
        int n = 1;
        for (JournalLineRequest req : requests) {
            BigDecimal debit = AccountingMoney.normalize(req.debitAmount());
            BigDecimal credit = AccountingMoney.normalize(req.creditAmount());
            if (AccountingMoney.isZero(debit) && AccountingMoney.isZero(credit)) {
                continue;
            }
            JournalEntryLine line = new JournalEntryLine();
            line.setOrganizationId(org);
            line.setJournalEntryId(entry.getId());
            line.setAccountId(req.accountId());
            line.setLineNumber(n++);
            line.setDescription(req.description());
            line.setDebitAmount(debit);
            line.setCreditAmount(credit);
            line.setCustomerId(req.customerId());
            line.setSupplierId(req.supplierId());
            line.setReference(req.reference());
            lines.save(line);
        }
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

    private void ensureInitialized(UUID orgId) {
        if (!accounts.existsByOrganizationIdAndSystemAccountKeyIsNotNull(orgId)) {
            Organization org = organizations.findById(orgId).orElseThrow();
            bootstrap.bootstrapOrganization(orgId, org.getFinancialYearStart());
        }
    }

    private UUID system(UUID org, SystemAccountKey key) {
        return accounts.findByOrganizationIdAndSystemAccountKey(org, key)
                .map(Account::getId)
                .orElseThrow(() ->
                        new BusinessException("System account missing: " + key + ". Initialize accounting first."));
    }

    private static JournalLineRequest debit(
            UUID accountId, BigDecimal amount, String desc, UUID customerId, UUID supplierId) {
        return new JournalLineRequest(
                accountId,
                desc,
                AccountingMoney.normalize(amount),
                AccountingMoney.zero(),
                customerId,
                supplierId,
                null);
    }

    private static JournalLineRequest credit(
            UUID accountId, BigDecimal amount, String desc, UUID customerId, UUID supplierId) {
        return new JournalLineRequest(
                accountId,
                desc,
                AccountingMoney.zero(),
                AccountingMoney.normalize(amount),
                customerId,
                supplierId,
                null);
    }

    private void addTaxCredit(
            List<JournalLineRequest> built, UUID org, SystemAccountKey key, BigDecimal amount, String number) {
        if (AccountingMoney.isPositive(amount)) {
            built.add(credit(system(org, key), amount, key.name() + " " + number, null, null));
        }
    }

    private void addTaxDebit(
            List<JournalLineRequest> built, UUID org, SystemAccountKey key, BigDecimal amount, String number) {
        if (AccountingMoney.isPositive(amount)) {
            built.add(debit(system(org, key), amount, key.name() + " " + number, null, null));
        }
    }

    private void addRoundOff(List<JournalLineRequest> built, UUID org, BigDecimal roundOff, String number) {
        BigDecimal ro = AccountingMoney.normalize(roundOff);
        if (ro.signum() > 0) {
            built.add(credit(system(org, SystemAccountKey.ROUND_OFF_INCOME), ro, "Round off " + number, null, null));
        } else if (ro.signum() < 0) {
            built.add(debit(
                    system(org, SystemAccountKey.ROUND_OFF_EXPENSE), ro.abs(), "Round off " + number, null, null));
        }
    }

    private void balanceAgainstAr(List<JournalLineRequest> built, UUID org, UUID customerId) {
        BigDecimal debit = AccountingMoney.zero();
        BigDecimal credit = AccountingMoney.zero();
        for (JournalLineRequest l : built) {
            debit = debit.add(AccountingMoney.normalize(l.debitAmount()));
            credit = credit.add(AccountingMoney.normalize(l.creditAmount()));
        }
        BigDecimal diff = debit.subtract(credit);
        if (diff.signum() > 0) {
            built.add(credit(system(org, SystemAccountKey.ROUND_OFF_INCOME), diff, "Balancing", null, null));
        } else if (diff.signum() < 0) {
            built.add(debit(
                    system(org, SystemAccountKey.ACCOUNTS_RECEIVABLE), diff.abs(), "AR balancing", customerId, null));
        }
    }

    private void balanceAgainstAp(List<JournalLineRequest> built, UUID org, UUID supplierId) {
        BigDecimal debit = AccountingMoney.zero();
        BigDecimal credit = AccountingMoney.zero();
        for (JournalLineRequest l : built) {
            debit = debit.add(AccountingMoney.normalize(l.debitAmount()));
            credit = credit.add(AccountingMoney.normalize(l.creditAmount()));
        }
        BigDecimal diff = debit.subtract(credit);
        if (diff.signum() > 0) {
            built.add(credit(system(org, SystemAccountKey.ACCOUNTS_PAYABLE), diff, "AP balancing", null, supplierId));
        } else if (diff.signum() < 0) {
            built.add(debit(system(org, SystemAccountKey.ROUND_OFF_EXPENSE), diff.abs(), "Balancing", null, null));
        }
    }

    private void linkDocument(SalesInvoice invoice, JournalEntry entry) {
        invoice.setAccountingStatus(AccountingStatus.POSTED);
        invoice.setPostedJournalEntryId(entry.getId());
        invoice.setAccountingPostedAt(OffsetDateTime.now());
    }

    private void linkPurchase(PurchaseInvoice invoice, JournalEntry entry) {
        invoice.setAccountingStatus(AccountingStatus.POSTED);
        invoice.setPostedJournalEntryId(entry.getId());
        invoice.setAccountingPostedAt(OffsetDateTime.now());
    }

    private void linkPayment(Payment payment, JournalEntry entry) {
        payment.setAccountingStatus(AccountingStatus.POSTED);
        payment.setPostedJournalEntryId(entry.getId());
        payment.setAccountingPostedAt(OffsetDateTime.now());
    }

    private JournalEntry loadJournal(UUID id) {
        return journals.findByIdAndOrganizationId(id, TenantContext.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Journal entry not found: " + id));
    }

    private JournalResponse toResponse(JournalEntry entry, boolean includeLines) {
        List<JournalEntryLine> journalLines =
                includeLines ? lines.findByJournalEntryIdOrderByLineNumberAsc(entry.getId()) : List.of();
        Map<UUID, Account> accountMap = new HashMap<>();
        for (JournalEntryLine line : journalLines) {
            accounts.findById(line.getAccountId()).ifPresent(a -> accountMap.put(a.getId(), a));
        }
        return AccountingMapper.toJournal(entry, journalLines, accountMap);
    }

    private static List<JournalValidationService.LineInput> toLineInputs(List<JournalLineRequest> requests) {
        return requests.stream()
                .filter(r -> !AccountingMoney.isZero(r.debitAmount()) || !AccountingMoney.isZero(r.creditAmount()))
                .map(r -> new JournalValidationService.LineInput(r.accountId(), r.debitAmount(), r.creditAmount()))
                .toList();
    }
}
