package com.flowledger.finance.voucher.adapter;

import static com.flowledger.finance.voucher.adapter.VoucherLineSupport.*;

import com.flowledger.accounting.domain.SystemAccountKey;
import com.flowledger.accounting.util.AccountingMoney;
import com.flowledger.finance.voucher.domain.VoucherType;
import com.flowledger.finance.voucher.dto.VoucherDtos.VoucherLineRequest;
import com.flowledger.sales.entity.SalesInvoice;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SalesVoucherBuilder {
    public static final String REFERENCE_TYPE = "SALES_INVOICE";

    private final SystemAccountLookup accounts;

    public SalesVoucherBuilder(SystemAccountLookup accounts) {
        this.accounts = accounts;
    }

    public BuiltDocumentVoucher build(SalesInvoice invoice) {
        UUID org = invoice.getOrganizationId();
        accounts.ensureInitialized(org);

        List<VoucherLineRequest> built = new ArrayList<>();
        built.add(debit(
                accounts.require(org, SystemAccountKey.ACCOUNTS_RECEIVABLE),
                invoice.getGrandTotal(),
                "AR " + invoice.getInvoiceNumber()));

        BigDecimal salesAmount = AccountingMoney.normalize(
                invoice.getTaxableAmount() != null && invoice.getTaxableAmount().signum() > 0
                        ? invoice.getTaxableAmount()
                        : invoice.getSubtotal());
        if (AccountingMoney.isPositive(salesAmount)) {
            built.add(credit(
                    accounts.require(org, SystemAccountKey.SALES), salesAmount, "Sales " + invoice.getInvoiceNumber()));
        }
        addTaxCredit(
                built, accounts, org, SystemAccountKey.OUTPUT_CGST, invoice.getCgstTotal(), invoice.getInvoiceNumber());
        addTaxCredit(
                built, accounts, org, SystemAccountKey.OUTPUT_SGST, invoice.getSgstTotal(), invoice.getInvoiceNumber());
        addTaxCredit(
                built, accounts, org, SystemAccountKey.OUTPUT_IGST, invoice.getIgstTotal(), invoice.getInvoiceNumber());
        addRoundOff(built, accounts, org, invoice.getRoundOff(), invoice.getInvoiceNumber());
        balanceAgainstAr(built, accounts, org);

        return new BuiltDocumentVoucher(
                VoucherType.SALES,
                invoice.getInvoiceDate() != null ? invoice.getInvoiceDate() : LocalDate.now(),
                null,
                "INR",
                BigDecimal.ONE,
                REFERENCE_TYPE,
                invoice.getId(),
                "Sales invoice " + invoice.getInvoiceNumber(),
                built);
    }
}
