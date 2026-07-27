package com.flowledger.migration.writer;

import static com.flowledger.migration.writer.WriterSupport.*;

import com.flowledger.accounting.domain.AccountType;
import com.flowledger.accounting.dto.AccountingDtos.AccountRequest;
import com.flowledger.accounting.repository.AccountRepository;
import com.flowledger.accounting.service.AccountService;
import com.flowledger.migration.domain.ImportModule;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CoaModuleWriter implements ModuleWriter {
    private final AccountService accounts;
    private final AccountRepository repo;

    public CoaModuleWriter(AccountService accounts, AccountRepository repo) {
        this.accounts = accounts;
        this.repo = repo;
    }

    @Override
    public ImportModule module() {
        return ImportModule.COA;
    }

    @Override
    public WriteResult write(UUID organizationId, Map<String, String> row) {
        String code = required(row, "accountCode").toUpperCase(Locale.ROOT);
        if (repo.existsByOrganizationIdAndAccountCode(organizationId, code)) {
            return WriteResult.skipped("Account already exists: " + code);
        }
        AccountType type = AccountType.ASSET;
        String typeStr = str(row, "accountType");
        if (typeStr != null) {
            try {
                type = AccountType.valueOf(typeStr.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid accountType: " + typeStr);
            }
        }
        UUID parentId = null;
        String parentCode = str(row, "parentCode");
        if (parentCode != null) {
            parentId = repo.findByOrganizationIdAndAccountCode(organizationId, parentCode.toUpperCase(Locale.ROOT))
                    .map(a -> a.getId())
                    .orElse(null);
        }
        var created = accounts.create(new AccountRequest(
                code,
                required(row, "accountName"),
                null,
                type,
                null,
                parentId,
                true,
                null,
                true,
                decimalOrZero(row, "openingDebit"),
                decimalOrZero(row, "openingCredit")));
        return WriteResult.imported(created.id(), "ACCOUNT");
    }
}
