package com.flowledger.finance.voucher.adapter;

import static com.flowledger.finance.voucher.adapter.VoucherLineSupport.*;

import com.flowledger.common.exception.BusinessException;
import com.flowledger.finance.voucher.domain.VoucherType;
import com.flowledger.payment.entity.Payment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Builds a CONTRA voucher: debit destination cash/bank, credit source cash/bank. */
@Component
public class ContraVoucherBuilder {
    public static final String REFERENCE_TYPE = "CONTRA";

    private final SystemAccountLookup accounts;

    public ContraVoucherBuilder(SystemAccountLookup accounts) {
        this.accounts = accounts;
    }

    public BuiltDocumentVoucher build(Payment payment) {
        if (payment.getFromAccountId() == null || payment.getToAccountId() == null) {
            throw new BusinessException("CONTRA requires fromAccountId and toAccountId");
        }
        if (payment.getFromAccountId().equals(payment.getToAccountId())) {
            throw new BusinessException("CONTRA from and to accounts must differ");
        }
        UUID org = payment.getOrganizationId();
        accounts.ensureInitialized(org);

        String label = "Contra " + payment.getPaymentNumber();
        List<com.flowledger.finance.voucher.dto.VoucherDtos.VoucherLineRequest> built = new ArrayList<>();
        built.add(debit(payment.getToAccountId(), payment.getAmount(), label));
        built.add(credit(payment.getFromAccountId(), payment.getAmount(), label));

        return new BuiltDocumentVoucher(
                VoucherType.CONTRA,
                payment.getPaymentDate() != null ? payment.getPaymentDate() : LocalDate.now(),
                null,
                "INR",
                BigDecimal.ONE,
                REFERENCE_TYPE,
                payment.getId(),
                label,
                built);
    }
}
