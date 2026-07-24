package com.flowledger.finance.voucher.service;

import com.flowledger.accounting.util.AccountingMoney;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.finance.voucher.dto.VoucherDtos.VoucherLineRequest;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class VoucherValidator {

    public record Totals(BigDecimal totalDebit, BigDecimal totalCredit) {}

    public Totals validateLines(List<VoucherLineRequest> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessException("Voucher requires at least one line");
        }
        if (lines.size() < 2) {
            throw new BusinessException("Voucher requires at least two lines");
        }
        BigDecimal totalDebit = AccountingMoney.zero();
        BigDecimal totalCredit = AccountingMoney.zero();
        for (VoucherLineRequest line : lines) {
            if (line.accountId() == null) {
                throw new BusinessException("Each voucher line requires an account");
            }
            BigDecimal debit = AccountingMoney.normalize(line.debit());
            BigDecimal credit = AccountingMoney.normalize(line.credit());
            boolean hasDebit = debit.signum() > 0;
            boolean hasCredit = credit.signum() > 0;
            if (hasDebit == hasCredit) {
                throw new BusinessException(
                        "Each voucher line must have either a debit or a credit amount, not both or neither");
            }
            if (debit.signum() < 0 || credit.signum() < 0) {
                throw new BusinessException("Voucher line amounts cannot be negative");
            }
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
        }
        if (totalDebit.compareTo(totalCredit) != 0) {
            throw new BusinessException("Voucher is not balanced: total debit " + totalDebit
                    + " does not equal total credit " + totalCredit);
        }
        return new Totals(totalDebit, totalCredit);
    }
}
