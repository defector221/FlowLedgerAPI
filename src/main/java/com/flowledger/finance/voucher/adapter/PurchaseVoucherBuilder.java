package com.flowledger.finance.voucher.adapter;

import static com.flowledger.finance.voucher.adapter.VoucherLineSupport.*;

import com.flowledger.accounting.domain.SystemAccountKey;
import com.flowledger.accounting.util.AccountingMoney;
import com.flowledger.finance.voucher.domain.VoucherType;
import com.flowledger.finance.voucher.dto.VoucherDtos.VoucherLineRequest;
import com.flowledger.purchase.entity.PurchaseInvoice;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PurchaseVoucherBuilder {
    public static final String REFERENCE_TYPE = "PURCHASE_INVOICE";

    private final SystemAccountLookup accounts;

    public PurchaseVoucherBuilder(SystemAccountLookup accounts) {
        this.accounts = accounts;
    }

    public BuiltDocumentVoucher build(PurchaseInvoice invoice) {
        UUID org = invoice.getOrganizationId();
        accounts.ensureInitialized(org);

        List<VoucherLineRequest> built = new ArrayList<>();
        BigDecimal purchaseAmount = AccountingMoney.normalize(
                invoice.getTaxableAmount() != null && invoice.getTaxableAmount().signum() > 0
                        ? invoice.getTaxableAmount()
                        : invoice.getSubtotal());
        if (AccountingMoney.isPositive(purchaseAmount)) {
            built.add(debit(
                    accounts.require(org, SystemAccountKey.PURCHASE),
                    purchaseAmount,
                    "Purchase " + invoice.getInvoiceNumber()));
        }
        addTaxDebit(
                built, accounts, org, SystemAccountKey.INPUT_CGST, invoice.getCgstTotal(), invoice.getInvoiceNumber());
        addTaxDebit(
                built, accounts, org, SystemAccountKey.INPUT_SGST, invoice.getSgstTotal(), invoice.getInvoiceNumber());
        addTaxDebit(
                built, accounts, org, SystemAccountKey.INPUT_IGST, invoice.getIgstTotal(), invoice.getInvoiceNumber());
        built.add(credit(
                accounts.require(org, SystemAccountKey.ACCOUNTS_PAYABLE),
                invoice.getGrandTotal(),
                "AP " + invoice.getInvoiceNumber()));
        addRoundOff(built, accounts, org, invoice.getRoundOff(), invoice.getInvoiceNumber());
        balanceAgainstAp(built, accounts, org);

        return new BuiltDocumentVoucher(
                VoucherType.PURCHASE,
                invoice.getInvoiceDate() != null ? invoice.getInvoiceDate() : LocalDate.now(),
                null,
                "INR",
                BigDecimal.ONE,
                REFERENCE_TYPE,
                invoice.getId(),
                "Purchase invoice " + invoice.getInvoiceNumber(),
                built);
    }
}
