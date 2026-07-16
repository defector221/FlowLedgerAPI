package com.flowledger.accounting.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.flowledger.accounting.bootstrap.ChartOfAccountsTemplate;
import com.flowledger.accounting.domain.SystemAccountKey;
import com.flowledger.accounting.entity.Account;
import com.flowledger.accounting.repository.AccountRepository;
import com.flowledger.accounting.repository.AccountingPeriodRepository;
import com.flowledger.accounting.repository.FiscalYearRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChartOfAccountsBootstrapServiceTest {
    @Mock
    AccountRepository accounts;

    @Mock
    FiscalYearRepository fiscalYears;

    @Mock
    AccountingPeriodRepository periods;

    @Captor
    ArgumentCaptor<Account> savedAccount;

    ChartOfAccountsBootstrapService service;
    UUID orgId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new ChartOfAccountsBootstrapService(accounts, fiscalYears, periods);
        when(accounts.existsByOrganizationIdAndSystemAccountKeyIsNotNull(orgId)).thenReturn(false);
        when(accounts.findByOrganizationIdAndAccountCode(any(), any())).thenReturn(Optional.empty());
        when(accounts.save(any())).thenAnswer(inv -> {
            Account a = inv.getArgument(0);
            if (a.getId() == null) {
                a.setId(UUID.randomUUID());
            }
            return a;
        });
        when(fiscalYears.findByOrganizationIdAndCurrentTrue(orgId)).thenReturn(Optional.empty());
    }

    @Test
    void bootstrapCreatesTemplateAccountsWithHierarchy() {
        service.bootstrapOrganization(orgId, "04-01");
        verify(accounts, times(ChartOfAccountsTemplate.NODES.size())).save(savedAccount.capture());

        Map<String, Account> byCode = new HashMap<>();
        for (Account account : savedAccount.getAllValues()) {
            byCode.put(account.getAccountCode(), account);
        }
        assertEquals(ChartOfAccountsTemplate.NODES.size(), byCode.size());
        assertTrue(byCode.containsKey("GRP-ASSET"));
        assertEquals(byCode.get("GRP-ASSET").getId(), byCode.get("1000").getParentAccountId());
        assertEquals(SystemAccountKey.CASH, byCode.get("1000").getSystemAccountKey());
    }

    @Test
    void bootstrapIsIdempotentWhenSystemAccountsExist() {
        when(accounts.existsByOrganizationIdAndSystemAccountKeyIsNotNull(orgId)).thenReturn(true);
        service.bootstrapOrganization(orgId, "04-01");
        verify(accounts, times(0)).save(any());
    }
}
