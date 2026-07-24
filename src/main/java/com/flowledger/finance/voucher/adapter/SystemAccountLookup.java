package com.flowledger.finance.voucher.adapter;

import com.flowledger.accounting.domain.SystemAccountKey;
import com.flowledger.accounting.entity.Account;
import com.flowledger.accounting.repository.AccountRepository;
import com.flowledger.accounting.service.ChartOfAccountsBootstrapService;
import com.flowledger.common.exception.BusinessException;
import com.flowledger.organization.entity.Organization;
import com.flowledger.organization.repository.OrganizationRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class SystemAccountLookup {
    private final AccountRepository accounts;
    private final OrganizationRepository organizations;
    private final ChartOfAccountsBootstrapService bootstrap;

    public SystemAccountLookup(
            AccountRepository accounts,
            OrganizationRepository organizations,
            ChartOfAccountsBootstrapService bootstrap) {
        this.accounts = accounts;
        this.organizations = organizations;
        this.bootstrap = bootstrap;
    }

    public void ensureInitialized(UUID orgId) {
        if (!accounts.existsByOrganizationIdAndSystemAccountKeyIsNotNull(orgId)) {
            Organization org = organizations.findById(orgId).orElseThrow();
            bootstrap.bootstrapOrganization(orgId, org.getFinancialYearStart());
        }
    }

    public UUID require(UUID org, SystemAccountKey key) {
        return accounts.findByOrganizationIdAndSystemAccountKey(org, key)
                .map(Account::getId)
                .orElseThrow(() ->
                        new BusinessException("System account missing: " + key + ". Initialize accounting first."));
    }

    public java.util.Optional<UUID> find(UUID org, SystemAccountKey key) {
        return accounts.findByOrganizationIdAndSystemAccountKey(org, key).map(Account::getId);
    }
}
