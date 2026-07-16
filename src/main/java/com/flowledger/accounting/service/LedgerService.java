package com.flowledger.accounting.service;

import com.flowledger.accounting.dto.AccountingDtos.LedgerLineResponse;
import com.flowledger.accounting.entity.Account;
import com.flowledger.accounting.entity.JournalEntry;
import com.flowledger.accounting.entity.JournalEntryLine;
import com.flowledger.accounting.repository.AccountRepository;
import com.flowledger.accounting.repository.JournalEntryLineRepository;
import com.flowledger.accounting.repository.JournalEntryRepository;
import com.flowledger.accounting.util.AccountingMoney;
import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.common.tenant.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {
    private final JournalEntryLineRepository lines;
    private final JournalEntryRepository journals;
    private final AccountRepository accounts;

    public LedgerService(
            JournalEntryLineRepository lines, JournalEntryRepository journals, AccountRepository accounts) {
        this.lines = lines;
        this.journals = journals;
        this.accounts = accounts;
    }

    @Transactional(readOnly = true)
    public List<LedgerLineResponse> accountLedger(UUID accountId, LocalDate from, LocalDate to) {
        UUID org = TenantContext.getOrganizationId();
        Account account = accounts.findByIdAndOrganizationId(accountId, org)
                .orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        return build(lines.findPostedForAccount(org, accountId, from, to), account);
    }

    @Transactional(readOnly = true)
    public List<LedgerLineResponse> customerLedger(UUID customerId, LocalDate from, LocalDate to) {
        UUID org = TenantContext.getOrganizationId();
        return build(lines.findPostedForCustomer(org, customerId, from, to), null);
    }

    @Transactional(readOnly = true)
    public List<LedgerLineResponse> supplierLedger(UUID supplierId, LocalDate from, LocalDate to) {
        UUID org = TenantContext.getOrganizationId();
        return build(lines.findPostedForSupplier(org, supplierId, from, to), null);
    }

    private List<LedgerLineResponse> build(List<JournalEntryLine> journalLines, Account forcedAccount) {
        List<LedgerLineResponse> result = new ArrayList<>();
        BigDecimal running = AccountingMoney.zero();
        for (JournalEntryLine line : journalLines) {
            JournalEntry entry = journals.findById(line.getJournalEntryId()).orElse(null);
            if (entry == null) {
                continue;
            }
            Account account = forcedAccount != null
                    ? forcedAccount
                    : accounts.findById(line.getAccountId()).orElse(null);
            running = running.add(AccountingMoney.normalize(line.getDebitAmount()))
                    .subtract(AccountingMoney.normalize(line.getCreditAmount()));
            result.add(new LedgerLineResponse(
                    entry.getId(),
                    entry.getEntryNumber(),
                    entry.getEntryDate(),
                    line.getDescription() != null ? line.getDescription() : entry.getDescription(),
                    line.getDebitAmount(),
                    line.getCreditAmount(),
                    running,
                    line.getAccountId(),
                    account != null ? account.getAccountCode() : null,
                    account != null ? account.getAccountName() : null,
                    line.getCustomerId(),
                    line.getSupplierId()));
        }
        return result;
    }
}
