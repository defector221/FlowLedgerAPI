package com.flowledger.migration.writer;

import static com.flowledger.migration.writer.WriterSupport.*;

import com.flowledger.accounting.repository.AccountRepository;
import com.flowledger.migration.domain.ImportModule;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class OpeningBalanceModuleWriter implements ModuleWriter {
    private final AccountRepository accounts;

    public OpeningBalanceModuleWriter(AccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    public ImportModule module() {
        return ImportModule.OPENING_BALANCE;
    }

    @Override
    public WriteResult write(UUID organizationId, Map<String, String> row) {
        String code = required(row, "accountCode").toUpperCase(Locale.ROOT);
        var account = accounts
                .findByOrganizationIdAndAccountCode(organizationId, code)
                .orElseThrow(() -> new IllegalArgumentException("Account not found: " + code));
        BigDecimal debit = decimalOrZero(row, "openingDebit");
        BigDecimal credit = decimalOrZero(row, "openingCredit");
        if (debit.signum() == 0 && credit.signum() == 0) {
            return WriteResult.skipped("Zero opening balance for " + code);
        }
        account.setOpeningDebit(debit);
        account.setOpeningCredit(credit);
        accounts.save(account);
        return WriteResult.imported(account.getId(), "OPENING_BALANCE");
    }
}
