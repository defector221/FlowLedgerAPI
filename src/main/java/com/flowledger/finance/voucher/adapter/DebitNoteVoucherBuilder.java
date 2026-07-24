package com.flowledger.finance.voucher.adapter;

import static com.flowledger.finance.voucher.adapter.VoucherLineSupport.*;

import com.flowledger.accounting.domain.SystemAccountKey;
import com.flowledger.finance.voucher.domain.VoucherType;
import com.flowledger.purchase.entity.DebitNote;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DebitNoteVoucherBuilder {
    public static final String REFERENCE_TYPE = "DEBIT_NOTE";

    private final SystemAccountLookup accounts;

    public DebitNoteVoucherBuilder(SystemAccountLookup accounts) {
        this.accounts = accounts;
    }

    public BuiltDocumentVoucher build(DebitNote debitNote) {
        UUID org = debitNote.getOrganizationId();
        accounts.ensureInitialized(org);

        return new BuiltDocumentVoucher(
                VoucherType.DEBIT_NOTE,
                debitNote.getDebitNoteDate() != null ? debitNote.getDebitNoteDate() : LocalDate.now(),
                null,
                "INR",
                BigDecimal.ONE,
                REFERENCE_TYPE,
                debitNote.getId(),
                "Debit note " + debitNote.getDebitNoteNumber(),
                List.of(
                        debit(
                                accounts.require(org, SystemAccountKey.ACCOUNTS_PAYABLE),
                                debitNote.getAmount(),
                                "Debit note " + debitNote.getDebitNoteNumber()),
                        credit(
                                accounts.require(org, SystemAccountKey.PURCHASE),
                                debitNote.getAmount(),
                                "Debit note " + debitNote.getDebitNoteNumber())));
    }
}
