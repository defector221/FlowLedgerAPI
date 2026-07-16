package com.flowledger.accounting.service;

import com.flowledger.accounting.domain.AccountStatus;
import com.flowledger.accounting.domain.AccountType;
import com.flowledger.accounting.dto.AccountingDtos.AccountRequest;
import com.flowledger.accounting.dto.AccountingDtos.AccountResponse;
import com.flowledger.accounting.dto.AccountingDtos.AccountTreeNode;
import com.flowledger.accounting.entity.Account;
import com.flowledger.accounting.repository.AccountRepository;
import com.flowledger.accounting.repository.JournalEntryLineRepository;
import com.flowledger.accounting.util.AccountingMoney;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.exception.ConflictException;
import com.flowledger.common.exception.ResourceNotFoundException;
import com.flowledger.common.tenant.TenantContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final AccountRepository repo;
    private final JournalEntryLineRepository journalLines;

    public AccountService(AccountRepository repo, JournalEntryLineRepository journalLines) {
        this.repo = repo;
        this.journalLines = journalLines;
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> list() {
        return repo.findByOrganizationIdOrderByAccountCodeAsc(TenantContext.getOrganizationId()).stream()
                .map(AccountService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AccountTreeNode> tree() {
        UUID org = TenantContext.getOrganizationId();
        List<Account> accounts = repo.findByOrganizationIdOrderByAccountCodeAsc(org);
        Map<UUID, List<Account>> childrenByParent = new HashMap<>();
        List<Account> roots = new ArrayList<>();
        for (Account account : accounts) {
            if (account.getParentAccountId() == null) {
                roots.add(account);
            } else {
                childrenByParent
                        .computeIfAbsent(account.getParentAccountId(), ignored -> new ArrayList<>())
                        .add(account);
            }
        }
        roots.sort(Comparator.comparing(Account::getAccountCode));
        return roots.stream().map(root -> toTreeNode(root, childrenByParent)).toList();
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
        account.setSystemAccountKey(null);
        account.setEditable(true);
        account.setDeletable(true);
        applyUserAccount(account, request, true);
        validateParent(account);
        validateSiblingName(account);
        return toResponse(repo.save(account));
    }

    @Transactional
    public AccountResponse update(UUID id, AccountRequest request) {
        Account account = load(id);
        if (account.isSystemAccount()) {
            return updateSystemAccount(account, request);
        }
        if (!account.isEditable()) {
            throw new BusinessException("This account cannot be edited");
        }
        String code = request.accountCode().trim();
        if (!code.equals(account.getAccountCode())
                && repo.existsByOrganizationIdAndAccountCode(account.getOrganizationId(), code)) {
            throw new ConflictException("Account code already exists: " + code);
        }
        account.setAccountCode(code);
        applyUserAccount(account, request, false);
        validateParent(account);
        validateSiblingName(account);
        return toResponse(repo.save(account));
    }

    @Transactional
    public void delete(UUID id) {
        Account account = load(id);
        if (account.isSystemAccount() || !account.isDeletable()) {
            throw new BusinessException("System accounts cannot be deleted");
        }
        if (repo.existsByOrganizationIdAndParentAccountId(account.getOrganizationId(), account.getId())) {
            throw new BusinessException("Cannot delete an account that has child accounts");
        }
        if (journalLines.existsByOrganizationIdAndAccountId(account.getOrganizationId(), account.getId())) {
            throw new BusinessException("Cannot delete an account that has journal entries");
        }
        repo.delete(account);
    }

    @Transactional
    public AccountResponse setActive(UUID id, boolean active) {
        Account account = load(id);
        account.setStatus(active ? AccountStatus.ACTIVE : AccountStatus.INACTIVE);
        return toResponse(repo.save(account));
    }

    private AccountResponse updateSystemAccount(Account account, AccountRequest request) {
        if (account.isGroupHeader()) {
            throw new BusinessException("Group header accounts cannot be modified");
        }
        if (request.accountType() != account.getAccountType()) {
            throw new BusinessException("System account type cannot be changed");
        }
        if (request.accountCode() != null
                && !request.accountCode().trim().equals(account.getAccountCode())) {
            throw new BusinessException("System account code cannot be changed");
        }
        if (request.parentAccountId() != null
                && !request.parentAccountId().equals(account.getParentAccountId())) {
            throw new BusinessException("System account parent cannot be changed");
        }
        if (request.openingDebit() != null || request.openingCredit() != null) {
            throw new BusinessException("System account opening balances cannot be changed");
        }
        if (account.isEditable()) {
            account.setAccountName(request.accountName().trim());
            account.setDescription(trimToNull(request.description()));
        }
        AccountStatus nextStatus = resolveStatus(request);
        if (nextStatus == AccountStatus.INACTIVE && account.getSystemAccountKey() != null) {
            throw new BusinessException("Posting system accounts cannot be deactivated");
        }
        account.setStatus(nextStatus);
        if (request.allowManualPosting() != null) {
            account.setAllowManualPosting(request.allowManualPosting());
        }
        return toResponse(repo.save(account));
    }

    private void applyUserAccount(Account account, AccountRequest request, boolean creating) {
        account.setAccountName(request.accountName().trim());
        account.setDescription(trimToNull(request.description()));
        if (creating || !account.isSystemAccount()) {
            account.setAccountType(request.accountType());
            account.setAccountSubType(request.accountSubType());
            account.setParentAccountId(request.parentAccountId());
        }
        account.setStatus(resolveStatus(request));
        account.setAllowManualPosting(request.allowManualPosting() == null || request.allowManualPosting());
        if (creating) {
            account.setOpeningDebit(AccountingMoney.normalize(request.openingDebit()));
            account.setOpeningCredit(AccountingMoney.normalize(request.openingCredit()));
        } else if (!account.isSystemAccount()) {
            account.setOpeningDebit(AccountingMoney.normalize(request.openingDebit()));
            account.setOpeningCredit(AccountingMoney.normalize(request.openingCredit()));
        }
    }

    private void validateParent(Account account) {
        UUID parentId = account.getParentAccountId();
        if (parentId == null) {
            return;
        }
        if (parentId.equals(account.getId())) {
            throw new BusinessException("An account cannot be its own parent");
        }
        Account parent = repo.findByIdAndOrganizationId(parentId, account.getOrganizationId())
                .orElseThrow(() -> new BusinessException("Parent account not found in this organization"));
        if (parent.getAccountType() != account.getAccountType()) {
            throw new BusinessException("Parent account must have the same account type");
        }
        detectCycle(account.getId(), parentId, account.getOrganizationId());
    }

    private void detectCycle(UUID accountId, UUID parentId, UUID orgId) {
        Set<UUID> visited = new HashSet<>();
        UUID current = parentId;
        while (current != null) {
            if (accountId != null && current.equals(accountId)) {
                throw new BusinessException("Circular account hierarchy is not allowed");
            }
            if (!visited.add(current)) {
                throw new BusinessException("Circular account hierarchy is not allowed");
            }
            current = repo.findByIdAndOrganizationId(current, orgId)
                    .map(Account::getParentAccountId)
                    .orElse(null);
        }
    }

    private void validateSiblingName(Account account) {
        UUID parentId = account.getParentAccountId();
        if (parentId == null) {
            return;
        }
        boolean duplicate;
        if (account.getId() == null) {
            duplicate = repo.existsByOrganizationIdAndParentAccountIdAndAccountNameIgnoreCase(
                    account.getOrganizationId(), parentId, account.getAccountName());
        } else {
            duplicate = repo.existsSiblingName(
                    account.getOrganizationId(), parentId, account.getAccountName(), account.getId());
        }
        if (duplicate) {
            throw new ConflictException("An account with this name already exists under the same parent");
        }
    }

    private static AccountStatus resolveStatus(AccountRequest request) {
        if (request.status() != null) {
            return request.status();
        }
        if (request.active() != null) {
            return request.active() ? AccountStatus.ACTIVE : AccountStatus.INACTIVE;
        }
        return AccountStatus.ACTIVE;
    }

    private Account load(UUID id) {
        return repo.findByIdAndOrganizationId(id, TenantContext.getOrganizationId())
                .orElseThrow(() -> new ResourceNotFoundException("Account not found: " + id));
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static AccountTreeNode toTreeNode(Account account, Map<UUID, List<Account>> childrenByParent) {
        List<Account> children = new ArrayList<>(childrenByParent.getOrDefault(account.getId(), List.of()));
        children.sort(Comparator.comparing(Account::getAccountCode));
        return new AccountTreeNode(
                account.getId(),
                account.getAccountCode(),
                account.getAccountName(),
                account.getDescription(),
                account.getAccountType(),
                account.getAccountSubType(),
                account.getParentAccountId(),
                account.getSystemAccountKey(),
                account.isSystemAccount(),
                account.isEditable(),
                account.isDeletable(),
                account.getStatus(),
                account.isActive(),
                account.isAllowManualPosting(),
                children.stream().map(child -> toTreeNode(child, childrenByParent)).toList());
    }

    private static AccountResponse toResponse(Account a) {
        return new AccountResponse(
                a.getId(),
                a.getOrganizationId(),
                a.getAccountCode(),
                a.getAccountName(),
                a.getDescription(),
                a.getAccountType(),
                a.getAccountSubType(),
                a.getParentAccountId(),
                a.getSystemAccountKey(),
                a.isSystemAccount(),
                a.isEditable(),
                a.isDeletable(),
                a.getStatus(),
                a.isActive(),
                a.isAllowManualPosting(),
                a.getOpeningDebit(),
                a.getOpeningCredit());
    }
}
