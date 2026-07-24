package com.flowledger.finance.voucher.adapter;

import static com.flowledger.finance.voucher.adapter.VoucherLineSupport.*;

import com.flowledger.accounting.domain.SystemAccountKey;
import com.flowledger.finance.voucher.domain.VoucherType;
import com.flowledger.purchase.entity.PurchaseReturn;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PurchaseReturnVoucherBuilder {
    public static final String REFERENCE_TYPE = "PURCHASE_RETURN";

    private final SystemAccountLookup accounts;

    public PurchaseReturnVoucherBuilder(SystemAccountLookup accounts) {
        this.accounts = accounts;
    }

    public BuiltDocumentVoucher build(PurchaseReturn purchaseReturn) {
        UUID org = purchaseReturn.getOrganizationId();
        accounts.ensureInitialized(org);

        return new BuiltDocumentVoucher(
                VoucherType.DEBIT_NOTE,
                purchaseReturn.getReturnDate() != null ? purchaseReturn.getReturnDate() : LocalDate.now(),
                null,
                "INR",
                BigDecimal.ONE,
                REFERENCE_TYPE,
                purchaseReturn.getId(),
                "Purchase return " + purchaseReturn.getReturnNumber(),
                List.of(
                        debit(
                                accounts.require(org, SystemAccountKey.ACCOUNTS_PAYABLE),
                                purchaseReturn.getGrandTotal(),
                                "Purchase return " + purchaseReturn.getReturnNumber()),
                        credit(
                                accounts.require(org, SystemAccountKey.PURCHASE),
                                purchaseReturn.getGrandTotal(),
                                "Purchase return " + purchaseReturn.getReturnNumber())));
    }
}
