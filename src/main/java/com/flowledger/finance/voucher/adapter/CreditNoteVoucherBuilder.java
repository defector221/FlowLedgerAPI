package com.flowledger.finance.voucher.adapter;

import static com.flowledger.finance.voucher.adapter.VoucherLineSupport.*;

import com.flowledger.accounting.domain.SystemAccountKey;
import com.flowledger.finance.voucher.domain.VoucherType;
import com.flowledger.sales.entity.CreditNote;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CreditNoteVoucherBuilder {
    public static final String REFERENCE_TYPE = "CREDIT_NOTE";

    private final SystemAccountLookup accounts;

    public CreditNoteVoucherBuilder(SystemAccountLookup accounts) {
        this.accounts = accounts;
    }

    public BuiltDocumentVoucher build(CreditNote creditNote) {
        UUID org = creditNote.getOrganizationId();
        accounts.ensureInitialized(org);

        return new BuiltDocumentVoucher(
                VoucherType.CREDIT_NOTE,
                creditNote.getCreditNoteDate() != null ? creditNote.getCreditNoteDate() : LocalDate.now(),
                null,
                "INR",
                BigDecimal.ONE,
                REFERENCE_TYPE,
                creditNote.getId(),
                "Credit note " + creditNote.getCreditNoteNumber(),
                List.of(
                        debit(
                                accounts.require(org, SystemAccountKey.SALES),
                                creditNote.getAmount(),
                                "Credit note " + creditNote.getCreditNoteNumber()),
                        credit(
                                accounts.require(org, SystemAccountKey.ACCOUNTS_RECEIVABLE),
                                creditNote.getAmount(),
                                "Credit note " + creditNote.getCreditNoteNumber())));
    }
}
