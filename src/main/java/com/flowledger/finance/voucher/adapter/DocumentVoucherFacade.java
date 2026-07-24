package com.flowledger.finance.voucher.adapter;

import com.flowledger.accounting.domain.AccountingStatus;
import com.flowledger.accounting.domain.JournalSource;
import com.flowledger.accounting.service.AccountingPostingService;
import com.flowledger.common.exception.ConflictException;
import com.flowledger.finance.config.FinanceProperties;
import com.flowledger.finance.voucher.domain.VoucherStatus;
import com.flowledger.finance.voucher.dto.VoucherDtos.PostingResult;
import com.flowledger.finance.voucher.dto.VoucherDtos.VoucherResponse;
import com.flowledger.finance.voucher.entity.Voucher;
import com.flowledger.finance.voucher.repository.VoucherRepository;
import com.flowledger.finance.voucher.service.PostingEngine;
import com.flowledger.finance.voucher.service.VoucherService;
import com.flowledger.payment.entity.Payment;
import com.flowledger.purchase.entity.DebitNote;
import com.flowledger.purchase.entity.PurchaseInvoice;
import com.flowledger.purchase.entity.PurchaseReturn;
import com.flowledger.sales.entity.CreditNote;
import com.flowledger.sales.entity.SalesInvoice;
import com.flowledger.sales.entity.SalesReturn;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Routes document posting through the voucher engine when enabled, otherwise falls back to
 * {@link AccountingPostingService} (kept for rollback).
 */
@Service
public class DocumentVoucherFacade {
    private final FinanceProperties finance;
    private final AccountingPostingService accounting;
    private final VoucherService vouchers;
    private final PostingEngine posting;
    private final VoucherRepository voucherRepository;
    private final SalesVoucherBuilder salesBuilder;
    private final PurchaseVoucherBuilder purchaseBuilder;
    private final PaymentVoucherBuilder paymentBuilder;
    private final CreditNoteVoucherBuilder creditNoteBuilder;
    private final SalesReturnVoucherBuilder salesReturnBuilder;
    private final PurchaseReturnVoucherBuilder purchaseReturnBuilder;
    private final DebitNoteVoucherBuilder debitNoteBuilder;
    private final ContraVoucherBuilder contraBuilder;

    public DocumentVoucherFacade(
            FinanceProperties finance,
            AccountingPostingService accounting,
            VoucherService vouchers,
            PostingEngine posting,
            VoucherRepository voucherRepository,
            SalesVoucherBuilder salesBuilder,
            PurchaseVoucherBuilder purchaseBuilder,
            PaymentVoucherBuilder paymentBuilder,
            CreditNoteVoucherBuilder creditNoteBuilder,
            SalesReturnVoucherBuilder salesReturnBuilder,
            PurchaseReturnVoucherBuilder purchaseReturnBuilder,
            DebitNoteVoucherBuilder debitNoteBuilder,
            ContraVoucherBuilder contraBuilder) {
        this.finance = finance;
        this.accounting = accounting;
        this.vouchers = vouchers;
        this.posting = posting;
        this.voucherRepository = voucherRepository;
        this.salesBuilder = salesBuilder;
        this.purchaseBuilder = purchaseBuilder;
        this.paymentBuilder = paymentBuilder;
        this.creditNoteBuilder = creditNoteBuilder;
        this.salesReturnBuilder = salesReturnBuilder;
        this.purchaseReturnBuilder = purchaseReturnBuilder;
        this.debitNoteBuilder = debitNoteBuilder;
        this.contraBuilder = contraBuilder;
    }

    @Transactional
    public void postSalesInvoice(SalesInvoice invoice) {
        if (!finance.isVoucherEngineEnabled()) {
            accounting.postSalesInvoice(invoice);
            return;
        }
        if (alreadyPosted(invoice.getAccountingStatus(), invoice.getPostedJournalEntryId())) {
            return;
        }
        UUID journalId = postBuilt(salesBuilder.build(invoice), invoice.getOrganizationId());
        link(invoice::setAccountingStatus, invoice::setPostedJournalEntryId, invoice::setAccountingPostedAt, journalId);
    }

    @Transactional
    public void postPurchaseInvoice(PurchaseInvoice invoice) {
        if (!finance.isVoucherEngineEnabled()) {
            accounting.postPurchaseInvoice(invoice);
            return;
        }
        if (alreadyPosted(invoice.getAccountingStatus(), invoice.getPostedJournalEntryId())) {
            return;
        }
        UUID journalId = postBuilt(purchaseBuilder.build(invoice), invoice.getOrganizationId());
        link(invoice::setAccountingStatus, invoice::setPostedJournalEntryId, invoice::setAccountingPostedAt, journalId);
    }

    @Transactional
    public void postPayment(Payment payment) {
        if (payment.getPaymentType() == Payment.Type.CONTRA) {
            postContra(payment);
            return;
        }
        if (!finance.isVoucherEngineEnabled()) {
            accounting.postPayment(payment);
            return;
        }
        if (alreadyPosted(payment.getAccountingStatus(), payment.getPostedJournalEntryId())) {
            return;
        }
        UUID journalId = postBuilt(paymentBuilder.build(payment), payment.getOrganizationId());
        link(payment::setAccountingStatus, payment::setPostedJournalEntryId, payment::setAccountingPostedAt, journalId);
    }

    @Transactional
    public void postContra(Payment payment) {
        if (!finance.isVoucherEngineEnabled()) {
            // Legacy AccountingPostingService has no CONTRA path; no-op when flag is off.
            return;
        }
        if (alreadyPosted(payment.getAccountingStatus(), payment.getPostedJournalEntryId())) {
            return;
        }
        UUID journalId = postBuilt(contraBuilder.build(payment), payment.getOrganizationId());
        link(payment::setAccountingStatus, payment::setPostedJournalEntryId, payment::setAccountingPostedAt, journalId);
    }

    /**
     * Posts a stock adjustment voucher when inventory/COGS accounts and amount are known.
     * Returns journal entry id, or null when skipped (engine disabled / null built).
     */
    @Transactional
    public UUID postStockAdjustment(UUID organizationId, BuiltDocumentVoucher built) {
        if (!finance.isVoucherEngineEnabled() || built == null || organizationId == null) {
            return null;
        }
        return postBuilt(built, organizationId);
    }

    @Transactional
    public void postSalesReturn(SalesReturn salesReturn) {
        if (!finance.isVoucherEngineEnabled()) {
            accounting.postSalesReturn(salesReturn);
            return;
        }
        postBuilt(salesReturnBuilder.build(salesReturn), salesReturn.getOrganizationId());
    }

    @Transactional
    public void postCreditNote(CreditNote creditNote) {
        if (!finance.isVoucherEngineEnabled()) {
            accounting.postCreditNote(creditNote);
            return;
        }
        postBuilt(creditNoteBuilder.build(creditNote), creditNote.getOrganizationId());
    }

    @Transactional
    public void postPurchaseReturn(PurchaseReturn purchaseReturn) {
        if (!finance.isVoucherEngineEnabled()) {
            accounting.postPurchaseReturn(purchaseReturn);
            return;
        }
        postBuilt(purchaseReturnBuilder.build(purchaseReturn), purchaseReturn.getOrganizationId());
    }

    @Transactional
    public void postDebitNote(DebitNote debitNote) {
        if (!finance.isVoucherEngineEnabled()) {
            // Legacy AccountingPostingService has no debit-note path; no-op when flag is off.
            return;
        }
        postBuilt(debitNoteBuilder.build(debitNote), debitNote.getOrganizationId());
    }

    @Transactional
    public void reverseDocument(
            UUID organizationId, String referenceType, UUID referenceId, JournalSource legacySource) {
        if (finance.isVoucherEngineEnabled()) {
            var existing = voucherRepository.findByOrganizationIdAndReferenceTypeAndReferenceIdAndDeletedAtIsNull(
                    organizationId, referenceType, referenceId);
            if (existing.isPresent()) {
                Voucher voucher = existing.get();
                if (voucher.getStatus() == VoucherStatus.POSTED) {
                    posting.reverse(voucher.getId());
                }
                return;
            }
        }
        accounting.reverseDocumentJournal(organizationId, legacySource, referenceId);
        if (legacySource != JournalSource.VOUCHER) {
            accounting.reverseDocumentJournal(organizationId, JournalSource.VOUCHER, referenceId);
        }
    }

    private UUID postBuilt(BuiltDocumentVoucher built, UUID organizationId) {
        var existing = voucherRepository.findByOrganizationIdAndReferenceTypeAndReferenceIdAndDeletedAtIsNull(
                organizationId, built.referenceType(), built.referenceId());
        if (existing.isPresent()) {
            return ensurePosted(existing.get());
        }
        try {
            VoucherResponse created = vouchers.createFromDocument(
                    built.type(),
                    built.voucherDate(),
                    built.branchId(),
                    built.currencyCode(),
                    built.exchangeRate(),
                    built.referenceType(),
                    built.referenceId(),
                    built.narration(),
                    built.lines());
            PostingResult result = posting.post(created.id());
            return result.journalEntryId();
        } catch (ConflictException ex) {
            Voucher raced = voucherRepository
                    .findByOrganizationIdAndReferenceTypeAndReferenceIdAndDeletedAtIsNull(
                            organizationId, built.referenceType(), built.referenceId())
                    .orElseThrow(() -> ex);
            return ensurePosted(raced);
        }
    }

    private UUID ensurePosted(Voucher voucher) {
        if (voucher.getStatus() == VoucherStatus.POSTED && voucher.getJournalEntryId() != null) {
            return voucher.getJournalEntryId();
        }
        if (voucher.getStatus() == VoucherStatus.APPROVED) {
            return posting.post(voucher.getId()).journalEntryId();
        }
        throw new ConflictException(
                "Existing voucher for document is not postable (status=" + voucher.getStatus() + ")");
    }

    private static boolean alreadyPosted(AccountingStatus status, UUID journalEntryId) {
        return status == AccountingStatus.POSTED && journalEntryId != null;
    }

    private static void link(
            java.util.function.Consumer<AccountingStatus> status,
            java.util.function.Consumer<UUID> journalId,
            java.util.function.Consumer<OffsetDateTime> postedAt,
            UUID journalEntryId) {
        status.accept(AccountingStatus.POSTED);
        journalId.accept(journalEntryId);
        postedAt.accept(OffsetDateTime.now());
    }
}
