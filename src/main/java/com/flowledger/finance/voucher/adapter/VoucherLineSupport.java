package com.flowledger.finance.voucher.adapter;

import com.flowledger.accounting.domain.SystemAccountKey;
import com.flowledger.accounting.util.AccountingMoney;
import com.flowledger.finance.voucher.dto.VoucherDtos.VoucherLineRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Shared debit/credit/tax/round-off helpers mirroring AccountingPostingService line construction. */
final class VoucherLineSupport {
    private VoucherLineSupport() {}

    static VoucherLineRequest debit(UUID accountId, BigDecimal amount, String description) {
        return new VoucherLineRequest(
                accountId,
                AccountingMoney.normalize(amount),
                AccountingMoney.zero(),
                description,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    static VoucherLineRequest credit(UUID accountId, BigDecimal amount, String description) {
        return new VoucherLineRequest(
                accountId,
                AccountingMoney.zero(),
                AccountingMoney.normalize(amount),
                description,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    static void addTaxCredit(
            List<VoucherLineRequest> built,
            SystemAccountLookup accounts,
            UUID org,
            SystemAccountKey key,
            BigDecimal amount,
            String number) {
        if (AccountingMoney.isPositive(amount)) {
            built.add(credit(accounts.require(org, key), amount, key.name() + " " + number));
        }
    }

    static void addTaxDebit(
            List<VoucherLineRequest> built,
            SystemAccountLookup accounts,
            UUID org,
            SystemAccountKey key,
            BigDecimal amount,
            String number) {
        if (AccountingMoney.isPositive(amount)) {
            built.add(debit(accounts.require(org, key), amount, key.name() + " " + number));
        }
    }

    static void addRoundOff(
            List<VoucherLineRequest> built,
            SystemAccountLookup accounts,
            UUID org,
            BigDecimal roundOff,
            String number) {
        BigDecimal ro = AccountingMoney.normalize(roundOff);
        if (ro.signum() > 0) {
            built.add(credit(accounts.require(org, SystemAccountKey.ROUND_OFF_INCOME), ro, "Round off " + number));
        } else if (ro.signum() < 0) {
            built.add(
                    debit(accounts.require(org, SystemAccountKey.ROUND_OFF_EXPENSE), ro.abs(), "Round off " + number));
        }
    }

    static void balanceAgainstAr(List<VoucherLineRequest> built, SystemAccountLookup accounts, UUID org) {
        BigDecimal diff = debitTotal(built).subtract(creditTotal(built));
        if (diff.signum() > 0) {
            built.add(credit(accounts.require(org, SystemAccountKey.ROUND_OFF_INCOME), diff, "Balancing"));
        } else if (diff.signum() < 0) {
            built.add(debit(accounts.require(org, SystemAccountKey.ACCOUNTS_RECEIVABLE), diff.abs(), "AR balancing"));
        }
    }

    static void balanceAgainstAp(List<VoucherLineRequest> built, SystemAccountLookup accounts, UUID org) {
        BigDecimal diff = debitTotal(built).subtract(creditTotal(built));
        if (diff.signum() > 0) {
            built.add(credit(accounts.require(org, SystemAccountKey.ACCOUNTS_PAYABLE), diff, "AP balancing"));
        } else if (diff.signum() < 0) {
            built.add(debit(accounts.require(org, SystemAccountKey.ROUND_OFF_EXPENSE), diff.abs(), "Balancing"));
        }
    }

    private static BigDecimal debitTotal(List<VoucherLineRequest> built) {
        BigDecimal total = AccountingMoney.zero();
        for (VoucherLineRequest line : built) {
            total = total.add(AccountingMoney.normalize(line.debit()));
        }
        return total;
    }

    private static BigDecimal creditTotal(List<VoucherLineRequest> built) {
        BigDecimal total = AccountingMoney.zero();
        for (VoucherLineRequest line : built) {
            total = total.add(AccountingMoney.normalize(line.credit()));
        }
        return total;
    }
}
