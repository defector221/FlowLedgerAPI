package com.flowledger.accounting.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.flowledger.accounting.domain.AccountStatus;
import com.flowledger.accounting.domain.AccountType;
import com.flowledger.accounting.domain.SystemAccountKey;
import com.flowledger.accounting.dto.AccountingDtos.AccountRequest;
import com.flowledger.accounting.entity.Account;
import com.flowledger.accounting.repository.AccountRepository;
import com.flowledger.accounting.repository.JournalEntryLineRepository;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.common.exception.ConflictException;
import com.flowledger.common.tenant.TenantContext;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {
    @Mock
    AccountRepository accounts;

    @Mock
    JournalEntryLineRepository journalLines;

    AccountService service;
    UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new AccountService(accounts, journalLines);
        TenantContext.setOrganizationId(orgId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void createRejectsDuplicateCode() {
        when(accounts.existsByOrganizationIdAndAccountCode(orgId, "6100")).thenReturn(true);
        AccountRequest request = new AccountRequest(
                "6100",
                "Petty Cash",
                null,
                AccountType.ASSET,
                null,
                null,
                true,
                AccountStatus.ACTIVE,
                true,
                BigDecimal.ZERO,
                BigDecimal.ZERO);
        assertThrows(ConflictException.class, () -> service.create(request));
    }

    @Test
    void deleteRejectsSystemAccount() {
        UUID id = UUID.randomUUID();
        Account account = systemAccount(id, SystemAccountKey.CASH);
        when(accounts.findByIdAndOrganizationId(id, orgId)).thenReturn(Optional.of(account));
        assertThrows(BusinessException.class, () -> service.delete(id));
    }

    @Test
    void deleteRejectsWhenJournalLinesExist() {
        UUID id = UUID.randomUUID();
        Account account = userAccount(id);
        when(accounts.findByIdAndOrganizationId(id, orgId)).thenReturn(Optional.of(account));
        when(accounts.existsByOrganizationIdAndParentAccountId(orgId, id)).thenReturn(false);
        when(journalLines.existsByOrganizationIdAndAccountId(orgId, id)).thenReturn(true);
        assertThrows(BusinessException.class, () -> service.delete(id));
    }

    @Test
    void updateSystemAccountAllowsNameChangeOnly() {
        UUID id = UUID.randomUUID();
        Account account = systemAccount(id, SystemAccountKey.SALES);
        account.setEditable(true);
        when(accounts.findByIdAndOrganizationId(id, orgId)).thenReturn(Optional.of(account));
        when(accounts.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AccountRequest request = new AccountRequest(
                "4000",
                "Sales Revenue",
                "Primary sales",
                AccountType.REVENUE,
                null,
                account.getParentAccountId(),
                true,
                AccountStatus.ACTIVE,
                true,
                null,
                null);
        var response = service.update(id, request);
        assertEquals("Sales Revenue", response.accountName());
        assertEquals(SystemAccountKey.SALES, response.systemAccountKey());
    }

    @Test
    void updateSystemAccountRejectsTypeChange() {
        UUID id = UUID.randomUUID();
        Account account = systemAccount(id, SystemAccountKey.SALES);
        when(accounts.findByIdAndOrganizationId(id, orgId)).thenReturn(Optional.of(account));
        AccountRequest request = new AccountRequest(
                "4000", "Sales", null, AccountType.EXPENSE, null, null, true, AccountStatus.ACTIVE, true, null, null);
        assertThrows(BusinessException.class, () -> service.update(id, request));
    }

    @Test
    void treeBuildsHierarchy() {
        UUID assetGroup = UUID.randomUUID();
        UUID cashId = UUID.randomUUID();
        Account group = groupAccount(assetGroup, "GRP-ASSET", "Assets", AccountType.ASSET);
        Account cash = systemAccount(cashId, SystemAccountKey.CASH);
        cash.setParentAccountId(assetGroup);
        when(accounts.findByOrganizationIdOrderByAccountCodeAsc(orgId)).thenReturn(List.of(group, cash));

        var tree = service.tree();
        assertEquals(1, tree.size());
        assertEquals("GRP-ASSET", tree.get(0).accountCode());
        assertEquals(1, tree.get(0).children().size());
        assertEquals("1000", tree.get(0).children().get(0).accountCode());
    }

    private Account systemAccount(UUID id, SystemAccountKey key) {
        Account account = new Account();
        account.setId(id);
        account.setOrganizationId(orgId);
        account.setAccountCode("1000");
        account.setAccountName("Cash");
        account.setAccountType(AccountType.ASSET);
        account.setSystemAccount(true);
        account.setSystemAccountKey(key);
        account.setEditable(true);
        account.setDeletable(false);
        account.setStatus(AccountStatus.ACTIVE);
        return account;
    }

    private Account userAccount(UUID id) {
        Account account = new Account();
        account.setId(id);
        account.setOrganizationId(orgId);
        account.setAccountCode("6100");
        account.setAccountName("Petty Cash");
        account.setAccountType(AccountType.ASSET);
        account.setSystemAccount(false);
        account.setEditable(true);
        account.setDeletable(true);
        account.setStatus(AccountStatus.ACTIVE);
        return account;
    }

    private Account groupAccount(UUID id, String code, String name, AccountType type) {
        Account account = new Account();
        account.setId(id);
        account.setOrganizationId(orgId);
        account.setAccountCode(code);
        account.setAccountName(name);
        account.setAccountType(type);
        account.setSystemAccount(true);
        account.setEditable(false);
        account.setDeletable(false);
        account.setStatus(AccountStatus.ACTIVE);
        return account;
    }
}
