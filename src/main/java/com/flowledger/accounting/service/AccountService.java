package com.flowledger.accounting.service;

import com.flowledger.accounting.dto.AccountingDtos.AccountRequest;
import com.flowledger.accounting.dto.AccountingDtos.AccountResponse;
import com.flowledger.accounting.entity.Account;
import com.flowledger.accounting.repository.AccountRepository;
import com.flowledger.accounting.util.AccountingMoney;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.exception.ConflictException;
import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.common.tenant.TenantContext;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final AccountRepository repo;

    public AccountService(AccountRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> list() {
        return repo.findByOrganizationIdOrderByAccountCodeAsc(TenantContext.getOrganizationId()).stream()
                .map(AccountService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse get(UUID id) {
        return toResponse(load(id));
    }

    @Transactional
    public AccountResponse create(AccountRequest request) {
        UUID org = TenantContext.getOrganizationId();
        String code = request.accountCode().trim();
        if (repo.existsByOrganizationIdAndAccountCode(org, code)) {
            throw new ConflictException("Account code already exists: " + code);
        }
        Account account = new Account();
        account.setOrganizationId(org);
        account.setAccountCode(code);
        account.setSystemAccount(false);
        apply(account, request);
        return toResponse(repo.save(account));
    }

    @Transactional
    public AccountResponse update(UUID id, AccountRequest request) {
        Account account = load(id);
        if (account.isSystemAccount()) {
            throw new BusinessException("System accounts cannot be modified; only active/allow-manual-posting flags apply");
        }
        String code = request.accountCode().trim();
        if (!code.equals(account.getAccountCode())
                && repo.existsByOrganizationIdAndAccountCode(account.getOrganizationId(), code)) {
            throw new ConflictException("Account code already exists: " + code);
        }
        account.setAccountCode(code);
        apply(account, request);
        return toResponse(repo.save(account));
    }

    @Transactional
    public AccountResponse setActive(UUID id, boolean active) {
        Account account = load(id);
        account.setActive(active);
        return toResponse(repo.save(account));
    }

    private void apply(Account account, AccountRequest request) {
        account.setAccountName(request.accountName());
        account.setAccountType(request.accountType());
        account.setAccountSubType(request.accountSubType());
        account.setParentAccountId(request.parentAccountId());
        account.setActive(request.active() == null || request.active());
        account.setAllowManualPosting(request.allowManualPosting() == null || request.allowManualPosting());
        account.setOpeningDebit(AccountingMoney.normalize(request.openingDebit()));
        account.setOpeningCredit(AccountingMoney.normalize(request.openingCredit()));
    }

    private Account load(UUID id) {
        return repo.findByIdAndOrganizationId(id, TenantContext.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id));
    }

    private static AccountResponse toResponse(Account a) {
        return new AccountResponse(
                a.getId(),
                a.getAccountCode(),
                a.getAccountName(),
                a.getAccountType(),
                a.getAccountSubType(),
                a.getParentAccountId(),
                a.getSystemAccountKey(),
                a.isSystemAccount(),
                a.isActive(),
                a.isAllowManualPosting(),
                a.getOpeningDebit(),
                a.getOpeningCredit());
    }
}
