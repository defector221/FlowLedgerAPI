package com.flowledger.finance.voucher.adapter;

import static com.flowledger.finance.voucher.adapter.VoucherLineSupport.*;

import com.flowledger.accounting.domain.SystemAccountKey;
import com.flowledger.finance.voucher.domain.VoucherType;
import com.flowledger.sales.entity.SalesReturn;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SalesReturnVoucherBuilder {
    public static final String REFERENCE_TYPE = "SALES_RETURN";

    private final SystemAccountLookup accounts;

    public SalesReturnVoucherBuilder(SystemAccountLookup accounts) {
        this.accounts = accounts;
    }

    public BuiltDocumentVoucher build(SalesReturn salesReturn) {
        UUID org = salesReturn.getOrganizationId();
        accounts.ensureInitialized(org);

        return new BuiltDocumentVoucher(
                VoucherType.CREDIT_NOTE,
                salesReturn.getReturnDate() != null ? salesReturn.getReturnDate() : LocalDate.now(),
                null,
                "INR",
                BigDecimal.ONE,
                REFERENCE_TYPE,
                salesReturn.getId(),
                "Sales return " + salesReturn.getReturnNumber(),
                List.of(
                        debit(
                                accounts.require(org, SystemAccountKey.SALES),
                                salesReturn.getGrandTotal(),
                                "Sales return " + salesReturn.getReturnNumber()),
                        credit(
                                accounts.require(org, SystemAccountKey.ACCOUNTS_RECEIVABLE),
                                salesReturn.getGrandTotal(),
                                "Sales return " + salesReturn.getReturnNumber())));
    }
}
