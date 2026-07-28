package com.flowledger.migration.writer;

import static com.flowledger.migration.writer.WriterSupport.*;

import com.flowledger.accounting.dto.AccountingDtos.JournalLineRequest;
import com.flowledger.accounting.dto.AccountingDtos.JournalRequest;
import com.flowledger.accounting.repository.AccountRepository;
import com.flowledger.accounting.service.AccountingPostingService;
import com.flowledger.migration.domain.ImportModule;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class JournalEntryModuleWriter implements DocumentModuleWriter {
    private final AccountingPostingService posting;
    private final AccountRepository accounts;

    public JournalEntryModuleWriter(AccountingPostingService posting, AccountRepository accounts) {
        this.posting = posting;
        this.accounts = accounts;
    }

    @Override
    public ImportModule module() {
        return ImportModule.JOURNAL_ENTRY;
    }

    @Override
    public String groupKey(Map<String, String> row) {
        String voucher = str(row, "voucherNumber");
        if (voucher != null) return voucher.toUpperCase(Locale.ROOT);
        return dateOrToday(row, "voucherDate") + "|" + str(row, "narration");
    }

    @Override
    public WriteResult writeDocument(UUID organizationId, List<Map<String, String>> rows) {
        if (rows.isEmpty()) return WriteResult.failed("Empty journal");
        Map<String, String> first = rows.get(0);
        List<JournalLineRequest> lines = new ArrayList<>();
        BigDecimal debitTotal = BigDecimal.ZERO;
        BigDecimal creditTotal = BigDecimal.ZERO;
        for (Map<String, String> row : rows) {
            String accountCode = required(row, "accountCode").toUpperCase(Locale.ROOT);
            var account = accounts.findByOrganizationIdAndAccountCode(organizationId, accountCode)
                    .orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountCode));
            BigDecimal debit = decimalOrZero(row, "debit");
            BigDecimal credit = decimalOrZero(row, "credit");
            debitTotal = debitTotal.add(debit);
            creditTotal = creditTotal.add(credit);
            lines.add(new JournalLineRequest(account.getId(), str(row, "narration"), debit, credit, null, null, null));
        }
        if (debitTotal.compareTo(creditTotal) != 0) {
            return WriteResult.failed("Unbalanced journal: debit=" + debitTotal + " credit=" + creditTotal);
        }
        var created = posting.createDraft(new JournalRequest(
                dateOrToday(first, "voucherDate"), null, str(first, "narration"), str(first, "voucherNumber"), lines));
        var posted = posting.postJournal(created.id());
        return WriteResult.imported(posted.id(), "JOURNAL_ENTRY");
    }
}
