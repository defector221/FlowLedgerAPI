package com.flowledger.accounting.service;

import com.flowledger.accounting.entity.JournalEntry;
import com.flowledger.accounting.entity.JournalEntryLine;
import com.flowledger.accounting.entity.LedgerBalance;
import com.flowledger.accounting.repository.LedgerBalanceRepository;
import com.flowledger.accounting.util.AccountingMoney;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerBalanceService {
    private final LedgerBalanceRepository balances;

    public LedgerBalanceService(LedgerBalanceRepository balances) {
        this.balances = balances;
    }

    @Transactional
    public void applyJournal(JournalEntry entry, List<JournalEntryLine> lines) {
        for (JournalEntryLine line : lines) {
            applyDelta(
                    entry.getOrganizationId(),
                    line.getAccountId(),
                    entry.getFiscalYearId(),
                    entry.getAccountingPeriodId(),
                    AccountingMoney.normalize(line.getDebitAmount()),
                    AccountingMoney.normalize(line.getCreditAmount()));
        }
    }

    @Transactional
    public void applyReversal(JournalEntry entry, List<JournalEntryLine> lines) {
        for (JournalEntryLine line : lines) {
            applyDelta(
                    entry.getOrganizationId(),
                    line.getAccountId(),
                    entry.getFiscalYearId(),
                    entry.getAccountingPeriodId(),
                    AccountingMoney.normalize(line.getCreditAmount()),
                    AccountingMoney.normalize(line.getDebitAmount()));
        }
    }

    private void applyDelta(
            java.util.UUID org,
            java.util.UUID accountId,
            java.util.UUID fiscalYearId,
            java.util.UUID periodId,
            BigDecimal debitDelta,
            BigDecimal creditDelta) {
        if (AccountingMoney.isZero(debitDelta) && AccountingMoney.isZero(creditDelta)) {
            return;
        }
        LedgerBalance bucket = balances.findBucket(org, accountId, fiscalYearId, periodId)
                .orElseGet(() -> {
                    LedgerBalance created = new LedgerBalance();
                    created.setOrganizationId(org);
                    created.setAccountId(accountId);
                    created.setFiscalYearId(fiscalYearId);
                    created.setAccountingPeriodId(periodId);
                    created.setDebitTotal(AccountingMoney.zero());
                    created.setCreditTotal(AccountingMoney.zero());
                    created.setBalance(AccountingMoney.zero());
                    return created;
                });
        bucket.setDebitTotal(AccountingMoney.normalize(bucket.getDebitTotal().add(debitDelta)));
        bucket.setCreditTotal(AccountingMoney.normalize(bucket.getCreditTotal().add(creditDelta)));
        bucket.setBalance(AccountingMoney.normalize(bucket.getDebitTotal().subtract(bucket.getCreditTotal())));
        balances.save(bucket);
    }
}
