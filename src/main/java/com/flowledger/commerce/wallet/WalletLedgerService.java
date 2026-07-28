package com.flowledger.commerce.wallet;

import com.flowledger.commerce.wallet.domain.WalletAccountType;
import com.flowledger.commerce.wallet.entity.CommerceWalletAccount;
import com.flowledger.commerce.wallet.entity.CommerceWalletEntry;
import com.flowledger.commerce.wallet.repository.CommerceWalletAccountRepository;
import com.flowledger.commerce.wallet.repository.CommerceWalletEntryRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class WalletLedgerService {
    private final CommerceWalletAccountRepository accounts;
    private final CommerceWalletEntryRepository entries;

    public WalletLedgerService(CommerceWalletAccountRepository accounts, CommerceWalletEntryRepository entries) {
        this.accounts = accounts;
        this.entries = entries;
    }

    @Transactional(readOnly = true)
    public List<CommerceWalletAccount> listAccounts(UUID customerId, UUID organizationId) {
        return accounts.findByCustomerIdAndOrganizationId(customerId, organizationId);
    }

    @Transactional(readOnly = true)
    public BigDecimal cashbackBalance(UUID customerId, UUID organizationId) {
        return accounts.findByCustomerIdAndOrganizationIdAndAccountTypeAndCurrency(
                        customerId, organizationId, WalletAccountType.CASHBACK, "INR")
                .map(CommerceWalletAccount::getBalance)
                .orElse(BigDecimal.ZERO);
    }

    public CommerceWalletAccount credit(
            UUID customerId,
            UUID organizationId,
            WalletAccountType accountType,
            BigDecimal amount,
            String referenceType,
            UUID referenceId,
            String note) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        CommerceWalletAccount account = findOrCreate(customerId, organizationId, accountType);
        account.setBalance(account.getBalance().add(amount));
        accounts.save(account);
        appendEntry(account.getId(), "CREDIT", amount, account.getBalance(), referenceType, referenceId, note);
        return account;
    }

    public CommerceWalletAccount debit(
            UUID customerId,
            UUID organizationId,
            WalletAccountType accountType,
            BigDecimal amount,
            String referenceType,
            UUID referenceId,
            String note) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        CommerceWalletAccount account = findOrCreate(customerId, organizationId, accountType);
        if (account.getBalance().compareTo(amount) < 0) {
            throw new IllegalArgumentException("Insufficient wallet balance");
        }
        account.setBalance(account.getBalance().subtract(amount));
        accounts.save(account);
        appendEntry(account.getId(), "DEBIT", amount, account.getBalance(), referenceType, referenceId, note);
        return account;
    }

    @Transactional(readOnly = true)
    public List<CommerceWalletEntry> history(UUID accountId) {
        return entries.findByAccountIdOrderByCreatedAtDesc(accountId);
    }

    private CommerceWalletAccount findOrCreate(UUID customerId, UUID organizationId, WalletAccountType accountType) {
        return accounts.findByCustomerIdAndOrganizationIdAndAccountTypeAndCurrency(
                        customerId, organizationId, accountType, "INR")
                .orElseGet(() -> {
                    CommerceWalletAccount a = new CommerceWalletAccount();
                    a.setCustomerId(customerId);
                    a.setOrganizationId(organizationId);
                    a.setAccountType(accountType);
                    a.setCurrency("INR");
                    a.setBalance(BigDecimal.ZERO);
                    return accounts.save(a);
                });
    }

    private void appendEntry(
            UUID accountId,
            String direction,
            BigDecimal amount,
            BigDecimal balanceAfter,
            String referenceType,
            UUID referenceId,
            String note) {
        CommerceWalletEntry entry = new CommerceWalletEntry();
        entry.setAccountId(accountId);
        entry.setDirection(direction);
        entry.setAmount(amount);
        entry.setBalanceAfter(balanceAfter);
        entry.setReferenceType(referenceType);
        entry.setReferenceId(referenceId);
        entry.setNote(note);
        entries.save(entry);
    }
}
