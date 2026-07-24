package com.flowledger.finance.voucher.adapter;

import static com.flowledger.finance.voucher.adapter.VoucherLineSupport.*;

import com.flowledger.accounting.domain.SystemAccountKey;
import com.flowledger.finance.voucher.domain.VoucherType;
import com.flowledger.payment.entity.Payment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class PaymentVoucherBuilder {
    public static final String REFERENCE_TYPE_RECEIPT = "CUSTOMER_RECEIPT";
    public static final String REFERENCE_TYPE_PAYMENT = "SUPPLIER_PAYMENT";

    private final SystemAccountLookup accounts;

    public PaymentVoucherBuilder(SystemAccountLookup accounts) {
        this.accounts = accounts;
    }

    public BuiltDocumentVoucher build(Payment payment) {
        UUID org = payment.getOrganizationId();
        accounts.ensureInitialized(org);

        UUID cashBank = accounts.require(org, SystemAccountKey.BANK);
        List<com.flowledger.finance.voucher.dto.VoucherDtos.VoucherLineRequest> built = new ArrayList<>();
        boolean receipt = payment.getPaymentType() == Payment.Type.RECEIPT;
        if (receipt) {
            built.add(debit(cashBank, payment.getAmount(), "Receipt " + payment.getPaymentNumber()));
            built.add(credit(
                    accounts.require(org, SystemAccountKey.ACCOUNTS_RECEIVABLE),
                    payment.getAmount(),
                    "Receipt " + payment.getPaymentNumber()));
        } else {
            built.add(debit(
                    accounts.require(org, SystemAccountKey.ACCOUNTS_PAYABLE),
                    payment.getAmount(),
                    "Payment " + payment.getPaymentNumber()));
            built.add(credit(cashBank, payment.getAmount(), "Payment " + payment.getPaymentNumber()));
        }

        return new BuiltDocumentVoucher(
                receipt ? VoucherType.RECEIPT : VoucherType.PAYMENT,
                payment.getPaymentDate() != null ? payment.getPaymentDate() : LocalDate.now(),
                null,
                "INR",
                BigDecimal.ONE,
                receipt ? REFERENCE_TYPE_RECEIPT : REFERENCE_TYPE_PAYMENT,
                payment.getId(),
                (receipt ? "Receipt " : "Payment ") + payment.getPaymentNumber(),
                built);
    }
}
